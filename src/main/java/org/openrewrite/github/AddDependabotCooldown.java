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
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.openrewrite.Validated.test;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddDependabotCooldown extends Recipe {
    @Option(displayName = "Default cooldown days",
            description = "The number of days to wait before considering a published dependency suitable for use (1-90). " +
                    "This delay allows security vendors time to identify potential compromises. " +
                    "Applied to all version types unless specific semver options are set.",
            example = "7",
            required = false)
    @Nullable
    Integer cooldownDays;

    @Option(displayName = "Semver major cooldown days",
            description = "The number of days to wait for major version updates (1-90). " +
                    "Only applies to package managers that support semantic versioning.",
            example = "14",
            required = false)
    @Nullable
    Integer semverMajorDays;

    @Option(displayName = "Semver minor cooldown days",
            description = "The number of days to wait for minor version updates (1-90). " +
                    "Only applies to package managers that support semantic versioning.",
            example = "7",
            required = false)
    @Nullable
    Integer semverMinorDays;

    @Option(displayName = "Semver patch cooldown days",
            description = "The number of days to wait for patch version updates (1-90). " +
                    "Only applies to package managers that support semantic versioning.",
            example = "3",
            required = false)
    @Nullable
    Integer semverPatchDays;

    @Option(displayName = "Include dependencies",
            description = "List of up to 150 dependencies to apply cooldown to. Supports wildcard patterns with `*`. " +
                    "If not specified, cooldown applies to all dependencies.",
            example = "lodash, react*",
            required = false)
    @Nullable
    List<String> include;

    @Option(displayName = "Exclude dependencies",
            description = "List of up to 150 dependencies to exempt from cooldown. Supports wildcard patterns with `*`. " +
                    "Exclude list takes precedence over include list.",
            example = "critical-security-package",
            required = false)
    @Nullable
    List<String> exclude;

    @Override
    public String getDisplayName() {
        return "Add cooldown periods to Dependabot configuration";
    }

    @Override
    public String getDescription() {
        return "Adds a `cooldown` section to each update configuration in Dependabot files. " +
                "Supports `default-days`, `semver-major-days`, `semver-minor-days`, `semver-patch-days`, " +
                "`include`, and `exclude` options. " +
                "This implements a security best practice where dependencies are not immediately adopted upon release, " +
                "allowing time for security vendors to identify potential supply chain compromises. " +
                "Cooldown applies only to version updates, not security updates. " +
                "[Read more about dependency cooldowns](https://blog.yossarian.net/2025/11/21/We-should-all-be-using-dependency-cooldowns). " +
                "[The available configuration options for dependabot are listed on GitHub](https://docs.github.com/en/code-security/supply-chain-security/keeping-your-dependencies-updated-automatically/configuration-options-for-dependency-updates).";
    }

    @Override
    public Set<String> getTags() {
        Set<String> tags = new HashSet<>();
        tags.add("dependabot");
        tags.add("dependencies");
        tags.add("github");
        tags.add("security");
        return tags;
    }

    @Override
    public Validated<Object> validate() {
        Validated<Object> validated = super.validate();
        int days = cooldownDays == null ? 7 : cooldownDays;
        Predicate<Integer> lessThanNinety = d -> d >= 1 && d <= 90;
        validated = validated.and(test("cooldownDays", "must be between 1 and 90", days, lessThanNinety));
        if (semverMajorDays != null) {
            validated = validated.and(test("semverMajorDays", "must be between 1 and 90", semverMajorDays, lessThanNinety));
        }
        if (semverMinorDays != null) {
            validated = validated.and(test("semverMinorDays", "must be between 1 and 90", semverMinorDays, lessThanNinety));
        }
        if (semverPatchDays != null) {
            validated = validated.and(test("semverPatchDays", "must be between 1 and 90", semverPatchDays, lessThanNinety));
        }
        if (include != null) {
            validated = validated.and(test("include", "list limited to 150 items", include, i -> i.size() <= 150));
        }
        if (exclude != null) {
            validated = validated.and(test("exclude", "list limited to 150 items", exclude, e -> e.size() <= 150));
        }
        return validated;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        int days = cooldownDays == null ? 7 : cooldownDays;

        return Preconditions.check(new FindSourceFiles(".github/dependabot.{yml,yaml}"), new YamlIsoVisitor<ExecutionContext>() {
            private final JsonPathMatcher packageEcosystemMatch = new JsonPathMatcher("$.updates[*].package-ecosystem");

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                if (packageEcosystemMatch.matches(getCursor())) {
                    // Mark that we need to add cooldown to this update entry
                    getCursor().dropParentUntil(Yaml.Mapping.class::isInstance).putMessage("ADD_COOLDOWN", true);
                }
                return super.visitMappingEntry(entry, ctx);
            }

            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                Yaml.Mapping m = super.visitMapping(mapping, ctx);

                if (Boolean.TRUE.equals(getCursor().getMessage("ADD_COOLDOWN"))) {
                    // Check if cooldown already exists
                    boolean hasCooldown = m.getEntries().stream()
                            .anyMatch(entry -> "cooldown".equals(entry.getKey().getValue()));

                    if (!hasCooldown) {
                        // Build cooldown YAML structure with all configured options
                        StringBuilder cooldownYaml = new StringBuilder();
                        cooldownYaml.append("cooldown:\n");
                        cooldownYaml.append("  default-days: ").append(days).append("\n");

                        if (semverMajorDays != null) {
                            cooldownYaml.append("  semver-major-days: ").append(semverMajorDays).append("\n");
                        }
                        if (semverMinorDays != null) {
                            cooldownYaml.append("  semver-minor-days: ").append(semverMinorDays).append("\n");
                        }
                        if (semverPatchDays != null) {
                            cooldownYaml.append("  semver-patch-days: ").append(semverPatchDays).append("\n");
                        }

                        if (include != null && !include.isEmpty()) {
                            cooldownYaml.append("  include:\n");
                            for (String dep : include) {
                                cooldownYaml.append("    - ").append(dep).append("\n");
                            }
                        }

                        if (exclude != null && !exclude.isEmpty()) {
                            cooldownYaml.append("  exclude:\n");
                            for (String dep : exclude) {
                                cooldownYaml.append("    - ").append(dep).append("\n");
                            }
                        }

                        // Parse the constructed YAML
                        Yaml.Documents documents = new YamlParser()
                                .parse(ctx, cooldownYaml.toString())
                                .map(Yaml.Documents.class::cast)
                                .findFirst()
                                .get();

                        Yaml.Mapping cooldownMapping = (Yaml.Mapping) documents.getDocuments().get(0).getBlock();
                        Yaml.Mapping.Entry cooldownEntry = cooldownMapping.getEntries().get(0);

                        // Auto-format the cooldown entry with proper indentation
                        cooldownEntry = autoFormat(cooldownEntry, ctx, getCursor());

                        // Add the cooldown entry to the mapping
                        m = m.withEntries(ListUtils.concat(m.getEntries(), cooldownEntry));
                    }
                }

                return m;
            }
        });
    }
}
