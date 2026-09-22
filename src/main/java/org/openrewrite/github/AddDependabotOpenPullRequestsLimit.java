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
import java.util.Set;

import static org.openrewrite.Validated.test;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddDependabotOpenPullRequestsLimit extends Recipe {
    private static final String KEY = "open-pull-requests-limit";

    @Option(displayName = "Open pull requests limit",
            description = "The maximum number of version update pull requests Dependabot keeps open for an update " +
                    "configuration. Set to `0` to temporarily disable version updates for the matched entries. " +
                    "Security update pull requests are not subject to this limit.",
            example = "5")
    Integer openPullRequestsLimit;

    @Option(displayName = "Package ecosystem",
            description = "Restrict the change to a single `package-ecosystem`, for example `gradle`. " +
                    "When omitted, every update configuration is changed.",
            example = "gradle",
            required = false)
    @Nullable
    String packageEcosystem;

    String displayName = "Add `open-pull-requests-limit` to Dependabot configuration";

    String description = "Adds an `open-pull-requests-limit` to each update configuration in Dependabot files, " +
                "and replaces an existing value when it differs. " +
                "The option caps the number of version update pull requests Dependabot keeps open; " +
                "setting it to `0` temporarily disables version updates for that `package-ecosystem`. " +
                "Security update pull requests are not subject to this limit and do not count towards it. " +
                "[The available configuration options for dependabot are listed on GitHub]" +
                "(https://docs.github.com/en/code-security/dependabot/working-with-dependabot/dependabot-options-reference#open-pull-requests-limit).";

    @Override
    public Set<String> getTags() {
        Set<String> tags = new HashSet<>();
        tags.add("dependabot");
        tags.add("dependencies");
        tags.add("github");
        return tags;
    }

    @Override
    public Validated<Object> validate() {
        Validated<Object> validated = super.validate();
        return validated.and(test("openPullRequestsLimit", "must be zero or greater",
                openPullRequestsLimit, limit -> limit >= 0));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/dependabot.{yml,yaml}"), new YamlIsoVisitor<ExecutionContext>() {
            private final JsonPathMatcher packageEcosystemMatch = new JsonPathMatcher("$.updates[*].package-ecosystem");

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                if (packageEcosystemMatch.matches(getCursor()) && entry.getValue() instanceof Yaml.Scalar) {
                    if (packageEcosystem == null || packageEcosystem.equals(((Yaml.Scalar) entry.getValue()).getValue())) {
                        getCursor().dropParentUntil(Yaml.Mapping.class::isInstance).putMessage(KEY, true);
                    }
                }
                return super.visitMappingEntry(entry, ctx);
            }

            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                Yaml.Mapping m = super.visitMapping(mapping, ctx);

                if (!Boolean.TRUE.equals(getCursor().pollMessage(KEY))) {
                    return m;
                }

                String limit = String.valueOf(openPullRequestsLimit);
                for (int i = 0; i < m.getEntries().size(); i++) {
                    Yaml.Mapping.Entry entry = m.getEntries().get(i);
                    if (!KEY.equals(entry.getKey().getValue()) || !(entry.getValue() instanceof Yaml.Scalar)) {
                        continue;
                    }
                    if (limit.equals(((Yaml.Scalar) entry.getValue()).getValue())) {
                        return m;
                    }
                    final int index = i;
                    final Yaml.Mapping.Entry updated = entry.withValue(
                            ((Yaml.Scalar) entry.getValue()).withValue(limit));
                    return m.withEntries(ListUtils.map(m.getEntries(), (idx, e) -> idx == index ? updated : e));
                }

                Yaml.Mapping.Entry added = entryFor(limit, ctx);
                if (added == null) {
                    return m;
                }
                return m.withEntries(ListUtils.concat(m.getEntries(), autoFormat(added, ctx, getCursor())));
            }

            private Yaml.Mapping.@Nullable Entry entryFor(String limit, ExecutionContext ctx) {
                return new YamlParser()
                        .parse(ctx, KEY + ": " + limit + "\n")
                        .map(Yaml.Documents.class::cast)
                        .findFirst()
                        .map(documents -> documents.getDocuments().get(0).getBlock())
                        .filter(Yaml.Mapping.class::isInstance)
                        .map(block -> ((Yaml.Mapping) block).getEntries().get(0))
                        .orElse(null);
            }
        });
    }
}
