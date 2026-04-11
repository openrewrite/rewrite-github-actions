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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.stream.Collectors;

public class PreferBlockStyleJobDependencies extends Recipe {
    @Getter
    final String displayName = "Prefer block style for job dependencies";

    @Getter
    final String description = "Convert flow-style `needs` sequences (e.g. `needs: [dep1, dep2]`) to block-style " +
            "in GitHub Actions workflow jobs when a job depends on more than one other job. " +
            "Block style improves readability and produces cleaner diffs in source control.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsWorkflow(), new YamlIsoVisitor<ExecutionContext>() {
            private final JsonPathMatcher needsMatcher = new JsonPathMatcher("$.jobs.*.needs");

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                if (needsMatcher.matches(getCursor()) && e.getValue() instanceof Yaml.Sequence) {
                    Yaml.Sequence sequence = (Yaml.Sequence) e.getValue();
                    if (sequence.getOpeningBracketPrefix() != null && sequence.getEntries().size() > 1) {
                        Yaml.Sequence blockSequence = sequence
                                .withOpeningBracketPrefix(null)
                                .withClosingBracketPrefix(null)
                                .withEntries(sequence.getEntries().stream()
                                        .map(seqEntry -> seqEntry
                                                .withDash(true)
                                                .withTrailingCommaPrefix(null)
                                                .withBlock(seqEntry.getBlock().withPrefix(" ")))
                                        .collect(Collectors.toList()));
                        return autoFormat(e.withValue(blockSequence), ctx, getCursor().getParentOrThrow());
                    }
                }
                return e;
            }
        });
    }
}
