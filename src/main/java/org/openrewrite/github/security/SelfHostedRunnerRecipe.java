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
package org.openrewrite.github.security;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Optional;

@Value
@EqualsAndHashCode(callSuper = false)
public class SelfHostedRunnerRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Find usage of self-hosted runners";
    }

    @Override
    public String getDescription() {
        return "Find workflows that use `self-hosted` runners, which may have security implications in public repositories " +
                "due to potential persistence between workflow runs and lack of isolation. Self-hosted runners should be " +
                "properly secured and ideally ephemeral. " +
                "Based on [zizmor's `self-hosted-runner` audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/self_hosted_runner.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml"),
                new SelfHostedRunnerVisitor()
        );
    }

    private static class SelfHostedRunnerVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            if ("runs-on".equals(mappingEntry.getKey().getValue())) {
                return checkRunsOn(mappingEntry);
            }

            return mappingEntry;
        }

        private static String getScalarValue(Yaml.Block block) {
            return block instanceof Yaml.Scalar ? ((Yaml.Scalar) block).getValue() : null;
        }

        private static Optional<String> getFirstSequenceValue(Yaml.Sequence sequence) {
            return sequence.getEntries().isEmpty() ? Optional.empty() :
                Optional.ofNullable(getScalarValue(sequence.getEntries().get(0).getBlock()));
        }


        private Yaml.Mapping.Entry checkRunsOn(Yaml.Mapping.Entry entry) {
            if (entry.getValue() instanceof Yaml.Scalar) {
                Yaml.Scalar scalar = (Yaml.Scalar) entry.getValue();
                return checkRunsOnValue(entry, scalar.getValue());
            }
            if (entry.getValue() instanceof Yaml.Sequence) {
                return checkRunsOnSequence(entry, (Yaml.Sequence) entry.getValue());
            }
            return entry;
        }

        private Yaml.Mapping.Entry checkRunsOnValue(Yaml.Mapping.Entry entry, String runsOnValue) {
            if ("self-hosted".equals(runsOnValue)) {
                return SearchResult.found(entry,
                        "Uses self-hosted runner which may have security implications in public repositories. " +
                                "Ensure runners are ephemeral and properly isolated.");
            }
            if (runsOnValue.contains("${{") && containsSelfHostedInMatrix(runsOnValue)) {
                return SearchResult.found(entry,
                        "Expression may expand to self-hosted runner. Verify that self-hosted runners are properly secured.");
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkRunsOnSequence(Yaml.Mapping.Entry entry, Yaml.Sequence sequence) {
            Optional<String> firstValue = getFirstSequenceValue(sequence);
            if (firstValue.isPresent() && "self-hosted".equals(firstValue.get())) {
                return SearchResult.found(entry,
                        "Uses self-hosted runner which may have security implications in public repositories. " +
                                "Ensure runners are ephemeral and properly isolated.");
            }

            return entry;
        }

        private boolean containsSelfHostedInMatrix(String expression) {
            // Simple check for matrix expressions that might expand to self-hosted
            // Look for matrix.* expressions and check if there's a matrix with self-hosted
            if (!expression.contains("matrix.")) {
                return false;
            }

            // Walk up to find the job and look for strategy.matrix
            Cursor current = getCursor();
            while (current != null) {
                Object value = current.getValue();
                if (value instanceof Yaml.Mapping) {
                    Yaml.Mapping mapping = (Yaml.Mapping) value;
                    for (Yaml.Mapping.Entry matrixEntry : mapping.getEntries()) {
                        if ("strategy".equals(matrixEntry.getKey().getValue()) && matrixEntry.getValue() instanceof Yaml.Mapping) {
                            return hasMatrixWithSelfHosted((Yaml.Mapping) matrixEntry.getValue());
                        }
                    }
                }
                current = current.getParent();
            }

            return false;
        }

        private boolean hasMatrixWithSelfHosted(Yaml.Mapping strategyMapping) {
            for (Yaml.Mapping.Entry strategyEntry : strategyMapping.getEntries()) {
                if ("matrix".equals(strategyEntry.getKey().getValue()) && strategyEntry.getValue() instanceof Yaml.Mapping) {
                    Yaml.Mapping matrixMapping = (Yaml.Mapping) strategyEntry.getValue();
                    return containsSelfHostedInMatrixValues(matrixMapping);
                }
            }
            return false;
        }

        private boolean containsSelfHostedInMatrixValues(Yaml.Mapping matrixMapping) {
            for (Yaml.Mapping.Entry matrixEntry : matrixMapping.getEntries()) {
                if (matrixEntry.getValue() instanceof Yaml.Sequence) {
                    Yaml.Sequence sequence = (Yaml.Sequence) matrixEntry.getValue();
                    for (Yaml.Sequence.Entry seqEntry : sequence.getEntries()) {
                        if (seqEntry.getBlock() instanceof Yaml.Scalar) {
                            String value = ((Yaml.Scalar) seqEntry.getBlock()).getValue();
                            if ("self-hosted".equals(value)) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }
    }
}
