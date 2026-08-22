/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.github;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.yaml.DeleteKey;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceDependabotReviewersWithCodeowners extends ScanningRecipe<ReplaceDependabotReviewersWithCodeowners.Accumulator> {

    private static final String DEFAULT_CODEOWNERS_PATH = ".github/CODEOWNERS";
    private static final List<String> CODEOWNERS_PRECEDENCE =
            unmodifiableList(asList(".github/CODEOWNERS", "CODEOWNERS", "docs/CODEOWNERS"));
    private static final Set<String> CODEOWNERS_LOCATIONS =
            unmodifiableSet(new LinkedHashSet<>(CODEOWNERS_PRECEDENCE));
    private static final Set<String> DEPENDABOT_LOCATIONS = unmodifiableSet(new LinkedHashSet<>(
            asList(".github/dependabot.yml", ".github/dependabot.yaml")));
    private static final String HEADER = "# Reviewers migrated from the Dependabot configuration";

    @Option(displayName = "`CODEOWNERS` path",
            description = "Where to write the migrated reviewers when the repository does not have a " +
                    "`CODEOWNERS` file yet. Defaults to `.github/CODEOWNERS`. When a `CODEOWNERS` file " +
                    "already exists in any of the locations GitHub recognizes, that file is appended to " +
                    "instead and this option is ignored.",
            required = false,
            example = "CODEOWNERS")
    @Nullable
    String codeownersPath;

    String displayName = "Replace Dependabot `reviewers` with `CODEOWNERS`";

    String description = "Replaces the [removed](https://github.blog/changelog/2025-04-29-dependabot-reviewers-configuration-option-being-replaced-by-code-owners/) " +
            "`reviewers` option in `.github/dependabot.yml` with equivalent `CODEOWNERS` entries. Each " +
            "reviewer is mapped onto the manifest files Dependabot updates for that `package-ecosystem` " +
            "and `directory`, so ownership stays as narrow as the Dependabot configuration was. Update " +
            "entries whose `package-ecosystem` has no known manifests are left untouched.";

    Set<String> tags = unmodifiableSet(new LinkedHashSet<>(asList("dependabot", "dependencies", "github")));

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile sourceFile = (SourceFile) tree;
                String path = normalize(sourceFile.getSourcePath());
                if (CODEOWNERS_LOCATIONS.contains(path)) {
                    acc.foundCodeowners(path, sourceFile);
                } else if (DEPENDABOT_LOCATIONS.contains(path) && sourceFile instanceof Yaml.Documents) {
                    new ReviewersScanner(acc).visit(sourceFile, ctx);
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        if (!acc.canMigrate() || acc.getExistingCodeowners() != null) {
            return emptyList();
        }
        String path = codeownersPath == null ? DEFAULT_CODEOWNERS_PATH : codeownersPath;
        String contents = HEADER + '\n' + String.join("\n", renderLines(acc.getOwnersByPattern())) + '\n';
        return PlainTextParser.builder().build().parse(contents)
                .map(created -> (SourceFile) created.withSourcePath(Paths.get(path)))
                .collect(toList());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (!acc.canMigrate()) {
            return TreeVisitor.noop();
        }
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile sourceFile = (SourceFile) tree;
                if (DEPENDABOT_LOCATIONS.contains(normalize(sourceFile.getSourcePath())) && sourceFile instanceof Yaml.Documents) {
                    String ecosystems = String.join("|", acc.getMigratedEcosystems());
                    return new DeleteKey("$.updates[?(@.package-ecosystem =~ '(" + ecosystems + ")')].reviewers", null)
                            .getVisitor().visitNonNull(sourceFile, ctx);
                }
                if (sourceFile instanceof PlainText && sourceFile.getSourcePath().equals(acc.getExistingCodeowners())) {
                    return append((PlainText) sourceFile, acc.getOwnersByPattern());
                }
                return sourceFile;
            }
        };
    }

    private static PlainText append(PlainText codeowners, Map<String, Set<String>> ownersByPattern) {
        Set<String> existingPatterns = new HashSet<>();
        for (String line : codeowners.getText().split("\r?\n", -1)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                existingPatterns.add(trimmed.split("\\s+")[0]);
            }
        }

        Map<String, Set<String>> missing = new LinkedHashMap<>();
        ownersByPattern.forEach((pattern, owners) -> {
            if (!existingPatterns.contains(pattern)) {
                missing.put(pattern, owners);
            }
        });
        if (missing.isEmpty()) {
            return codeowners;
        }

        String existing = codeowners.getText();
        String newline = existing.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder text = new StringBuilder(existing);
        if (!existing.isEmpty()) {
            if (existing.charAt(existing.length() - 1) != '\n') {
                text.append(newline);
            }
            text.append(newline);
        }
        text.append(HEADER);
        for (String line : renderLines(missing)) {
            text.append(newline).append(line);
        }
        // Match the trailing newline convention the file already used
        if (existing.endsWith("\n")) {
            text.append(newline);
        }
        return codeowners.withText(text.toString());
    }

    private static List<String> renderLines(Map<String, Set<String>> ownersByPattern) {
        List<String> lines = new ArrayList<>(ownersByPattern.size());
        ownersByPattern.forEach((pattern, owners) -> lines.add(pattern + ' ' + String.join(" ", owners)));
        return lines;
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static class ReviewersScanner extends YamlIsoVisitor<ExecutionContext> {
        private final Accumulator acc;

        private ReviewersScanner(Accumulator acc) {
            this.acc = acc;
        }

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            if ("updates".equals(entry.getKey().getValue()) && entry.getValue() instanceof Yaml.Sequence) {
                for (Yaml.Sequence.Entry update : ((Yaml.Sequence) entry.getValue()).getEntries()) {
                    if (update.getBlock() instanceof Yaml.Mapping) {
                        collect((Yaml.Mapping) update.getBlock());
                    }
                }
            }
            return super.visitMappingEntry(entry, ctx);
        }

        private void collect(Yaml.Mapping update) {
            List<String> reviewers = scalars(value(update, "reviewers"));
            if (reviewers.isEmpty()) {
                return;
            }
            String ecosystem = scalar(value(update, "package-ecosystem"));
            if (!DependabotEcosystemManifests.isKnown(ecosystem)) {
                return;
            }

            List<String> directories = scalars(value(update, "directories"));
            if (directories.isEmpty()) {
                String directory = scalar(value(update, "directory"));
                directories = singletonList(directory == null ? "/" : directory);
            }

            for (String directory : directories) {
                for (String pattern : DependabotEcosystemManifests.patternsFor(ecosystem, directory)) {
                    Set<String> owners = acc.getOwnersByPattern().computeIfAbsent(pattern, p -> new LinkedHashSet<>());
                    for (String reviewer : reviewers) {
                        owners.add(reviewer.startsWith("@") ? reviewer : "@" + reviewer);
                    }
                }
            }
            acc.getMigratedEcosystems().add(ecosystem);
        }

        private static Yaml.@Nullable Block value(Yaml.Mapping mapping, String key) {
            for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
                if (key.equals(entry.getKey().getValue())) {
                    return entry.getValue();
                }
            }
            return null;
        }

        private static @Nullable String scalar(Yaml.@Nullable Block block) {
            return block instanceof Yaml.Scalar ? ((Yaml.Scalar) block).getValue() : null;
        }

        private static List<String> scalars(Yaml.@Nullable Block block) {
            if (!(block instanceof Yaml.Sequence)) {
                return emptyList();
            }
            List<String> values = new ArrayList<>();
            for (Yaml.Sequence.Entry entry : ((Yaml.Sequence) block).getEntries()) {
                String value = scalar(entry.getBlock());
                if (value != null && !value.trim().isEmpty()) {
                    values.add(value.trim());
                }
            }
            return values;
        }
    }

    @Getter
    public static class Accumulator {
        private final Map<String, Set<String>> ownersByPattern = new LinkedHashMap<>();
        private final Set<String> migratedEcosystems = new LinkedHashSet<>();

        private @Nullable Path existingCodeowners;
        private int existingCodeownersPrecedence = Integer.MAX_VALUE;

        // A CODEOWNERS not parsed as plain text cannot be appended to, and deleting the reviewers
        // without recording them anywhere would lose the configuration
        private boolean codeownersIsAppendable = true;

        void foundCodeowners(String path, SourceFile sourceFile) {
            // GitHub honors only one CODEOWNERS: .github wins over the root, which wins over docs
            int precedence = CODEOWNERS_PRECEDENCE.indexOf(path);
            if (precedence >= existingCodeownersPrecedence) {
                return;
            }
            existingCodeownersPrecedence = precedence;
            codeownersIsAppendable = sourceFile instanceof PlainText;
            existingCodeowners = codeownersIsAppendable ? sourceFile.getSourcePath() : null;
        }

        boolean canMigrate() {
            return !ownersByPattern.isEmpty() && codeownersIsAppendable;
        }
    }
}
