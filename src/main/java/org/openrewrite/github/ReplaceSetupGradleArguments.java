/*
 * Copyright 2026 the original author or authors.
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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class ReplaceSetupGradleArguments extends Recipe {

    private static final Pattern SETUP_GRADLE_USES = Pattern.compile(
            "^(gradle/actions/setup-gradle|gradle/gradle-build-action)(?:@.+)?$");
    private static final String SETUP_GRADLE = "gradle/actions/setup-gradle";
    private static final String GRADLE_BUILD_ACTION = "gradle/gradle-build-action";

    @Getter
    final String displayName = "Replace `gradle/actions/setup-gradle` `arguments` with a `run` step";

    @Getter
    final String description = "The `arguments` input of `gradle/actions/setup-gradle` is removed in v4 and no longer " +
            "runs the build. Split each matching step into `setup-gradle` plus `run: ./gradlew <arguments>`, mapping " +
            "`build-root-directory` to `working-directory`. Also updates `gradle/gradle-build-action`, which was " +
            "renamed to `setup-gradle` and has the same removed input. Consecutive argument-only steps share one " +
            "setup step. See the [setup-gradle migration guide](https://github.com/gradle/actions/blob/main/docs/deprecation-upgrade-guide.md#using-the-action-to-execute-gradle-via-the-arguments-parameter-is-deprecated).";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    @Override
    public Set<String> getTags() {
        Set<String> tags = new HashSet<>();
        tags.add("github");
        tags.add("gradle");
        tags.add("actions");
        return tags;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new IsGitHubActionsWorkflow().getVisitor(),
                        new IsGitHubActionDefinition().getVisitor()),
                new ReplaceSetupGradleArgumentsVisitor());
    }

    private static class ReplaceSetupGradleArgumentsVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Sequence visitSequence(Yaml.Sequence sequence, ExecutionContext ctx) {
            Yaml.Sequence seq = super.visitSequence(sequence, ctx);
            if (!isStepsSequence()) {
                return seq;
            }

            List<Yaml.Sequence.Entry> rewritten = new ArrayList<>();
            boolean changed = false;
            boolean jobHasSetup = false;

            for (Yaml.Sequence.Entry entry : seq.getEntries()) {
                StepModel step = StepModel.from(entry);
                if (step != null && step.isSetupGradle() && !step.hasArguments()) {
                    jobHasSetup = true;
                }

                if (step == null || !step.isSetupGradle() || !step.hasArguments()) {
                    rewritten.add(entry);
                    continue;
                }

                changed = true;
                boolean insertSetup = !jobHasSetup || step.hasRemainingWith();
                if (insertSetup) {
                    rewritten.add(step.toSetupEntry(entry, ctx, this));
                    jobHasSetup = true;
                }
                rewritten.add(step.toRunEntry(entry, ctx, this));
            }

            return changed ? seq.withEntries(rewritten) : seq;
        }

        private boolean isStepsSequence() {
            if (getCursor().getParent() == null) {
                return false;
            }
            Object parent = getCursor().getParent().getValue();
            return parent instanceof Yaml.Mapping.Entry &&
                    "steps".equals(((Yaml.Mapping.Entry) parent).getKey().getValue());
        }

        private Yaml.Mapping.Entry mappingEntry(String yaml, String prefix, ExecutionContext ctx) {
            Yaml.Documents documents = new YamlParser()
                    .parse(ctx, yaml)
                    .map(Yaml.Documents.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Could not parse YAML: " + yaml));
            Yaml.Mapping mapping = (Yaml.Mapping) documents.getDocuments().get(0).getBlock();
            return mapping.getEntries().get(0).withPrefix(prefix);
        }
    }

    private static class StepModel {
        private final Yaml.Mapping mapping;
        private final Yaml.Mapping.Entry usesEntry;
        private final String uses;
        private final Yaml.Mapping.@Nullable Entry withEntry;
        private final @Nullable String arguments;
        private final Yaml.Mapping.@Nullable Entry buildRootDirectoryEntry;
        private final List<Yaml.Mapping.Entry> remainingWith;

        private StepModel(Yaml.Mapping mapping, Yaml.Mapping.Entry usesEntry, String uses,
                          Yaml.Mapping.@Nullable Entry withEntry, @Nullable String arguments,
                          Yaml.Mapping.@Nullable Entry buildRootDirectoryEntry,
                          List<Yaml.Mapping.Entry> remainingWith) {
            this.mapping = mapping;
            this.usesEntry = usesEntry;
            this.uses = uses;
            this.withEntry = withEntry;
            this.arguments = arguments;
            this.buildRootDirectoryEntry = buildRootDirectoryEntry;
            this.remainingWith = remainingWith;
        }

        static @Nullable StepModel from(Yaml.Sequence.Entry entry) {
            if (!(entry.getBlock() instanceof Yaml.Mapping)) {
                return null;
            }
            Yaml.Mapping mapping = (Yaml.Mapping) entry.getBlock();
            Yaml.Mapping.Entry usesEntry = null;
            Yaml.Mapping.Entry withEntry = null;
            for (Yaml.Mapping.Entry mappingEntry : mapping.getEntries()) {
                String key = mappingEntry.getKey().getValue();
                if ("uses".equals(key)) {
                    usesEntry = mappingEntry;
                } else if ("with".equals(key)) {
                    withEntry = mappingEntry;
                }
            }
            if (usesEntry == null || !(usesEntry.getValue() instanceof Yaml.Scalar)) {
                return null;
            }
            String uses = ((Yaml.Scalar) usesEntry.getValue()).getValue();
            if (!SETUP_GRADLE_USES.matcher(uses).matches()) {
                return null;
            }

            String arguments = null;
            Yaml.Mapping.Entry buildRootDirectoryEntry = null;
            List<Yaml.Mapping.Entry> remainingWith = new ArrayList<>();
            if (withEntry != null && withEntry.getValue() instanceof Yaml.Mapping) {
                for (Yaml.Mapping.Entry with : ((Yaml.Mapping) withEntry.getValue()).getEntries()) {
                    String key = with.getKey().getValue();
                    if ("arguments".equals(key)) {
                        arguments = scalarValue(with.getValue());
                    } else if ("build-root-directory".equals(key)) {
                        buildRootDirectoryEntry = with;
                    } else {
                        remainingWith.add(with);
                    }
                }
            }
            return new StepModel(mapping, usesEntry, uses, withEntry, arguments, buildRootDirectoryEntry, remainingWith);
        }

        boolean isSetupGradle() {
            return true;
        }

        boolean hasArguments() {
            return arguments != null && !arguments.trim().isEmpty();
        }

        boolean hasRemainingWith() {
            return !remainingWith.isEmpty();
        }

        Yaml.Sequence.Entry toSetupEntry(Yaml.Sequence.Entry original, ExecutionContext ctx,
                                         ReplaceSetupGradleArgumentsVisitor visitor) {
            List<Yaml.Mapping.Entry> entries = new ArrayList<>();
            entries.add(visitor.mappingEntry("name: Setup Gradle", firstKeyPrefix(), ctx));
            entries.add(usesEntry
                    .withPrefix(nestedKeyPrefix())
                    .withValue(((Yaml.Scalar) usesEntry.getValue()).withValue(normalizeUses(uses))));
            if (withEntry != null && !remainingWith.isEmpty()) {
                Yaml.Mapping withMapping = (Yaml.Mapping) withEntry.getValue();
                entries.add(withEntry
                        .withPrefix(nestedKeyPrefix())
                        .withValue(withMapping.withEntries(remainingWith)));
            }
            return original.withBlock(mapping.withEntries(entries));
        }

        Yaml.Sequence.Entry toRunEntry(Yaml.Sequence.Entry original, ExecutionContext ctx,
                                       ReplaceSetupGradleArgumentsVisitor visitor) {
            List<Yaml.Mapping.Entry> entries = new ArrayList<>();
            for (Yaml.Mapping.Entry mappingEntry : mapping.getEntries()) {
                String key = mappingEntry.getKey().getValue();
                if ("uses".equals(key) || "with".equals(key)) {
                    continue;
                }
                entries.add(mappingEntry);
            }
            String nextPrefix = entries.isEmpty() ? firstKeyPrefix() : nestedKeyPrefix();
            if (buildRootDirectoryEntry != null) {
                Yaml.Scalar key = (Yaml.Scalar) buildRootDirectoryEntry.getKey();
                entries.add(buildRootDirectoryEntry
                        .withPrefix(nextPrefix)
                        .withKey(key.withValue("working-directory")));
                nextPrefix = nestedKeyPrefix();
            }
            entries.add(visitor.mappingEntry(
                    "run: " + yamlQuoted("./gradlew " + arguments.trim()),
                    nextPrefix,
                    ctx));
            return original.withBlock(mapping.withEntries(entries));
        }

        private String firstKeyPrefix() {
            return mapping.getEntries().get(0).getPrefix();
        }

        private String nestedKeyPrefix() {
            for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
                if (entry.getPrefix().indexOf('\n') >= 0) {
                    return entry.getPrefix();
                }
            }
            return "\n        ";
        }

        private static String normalizeUses(String uses) {
            if (uses.startsWith(GRADLE_BUILD_ACTION)) {
                return SETUP_GRADLE + uses.substring(GRADLE_BUILD_ACTION.length());
            }
            return uses;
        }

        private static @Nullable String scalarValue(Yaml.Block block) {
            return block instanceof Yaml.Scalar ? ((Yaml.Scalar) block).getValue() : null;
        }

        private static String yamlQuoted(String value) {
            if (value.isEmpty() ||
                    Character.isWhitespace(value.charAt(0)) ||
                    Character.isWhitespace(value.charAt(value.length() - 1)) ||
                    value.indexOf(':') >= 0 ||
                    value.indexOf('#') >= 0 ||
                    value.indexOf('\n') >= 0 ||
                    value.indexOf('{') >= 0 ||
                    value.indexOf('}') >= 0 ||
                    value.indexOf('[') >= 0 ||
                    value.indexOf(']') >= 0 ||
                    value.indexOf('\'') >= 0 ||
                    value.indexOf('"') >= 0) {
                return "'" + value.replace("'", "''") + "'";
            }
            return value;
        }
    }
}
