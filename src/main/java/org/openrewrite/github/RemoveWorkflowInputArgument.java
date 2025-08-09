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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.time.Duration;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Value;

import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveWorkflowInputArgument extends Recipe {

    @Option(displayName = "Workflow reference",
            description = "The workflow reference to match (e.g., `org/repo/.github/workflows/myWorkflow.yml`).",
            example = "org/repo/.github/workflows/myWorkflow.yml")
    String workflowReference;

    @Option(displayName = "Version",
            description = "The version of the workflow to match (e.g., `v1.2.3`).",
            example = "v1.2.3")
    String version;

    @Option(displayName = "Input argument name",
            description = "The name of the input argument to remove.",
            example = "myInputToRemove")
    String inputArgumentName;

    @Override
    public String getDisplayName() {
        return "Remove workflow input argument";
    }

    @Override
    public String getDescription() {
        return "Remove a specific input argument from calls to a reusable workflow.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JsonPathMatcher jobsMatcher = new JsonPathMatcher("$.jobs.*");
        String expectedReference = workflowReference + "@" + version;

        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                Yaml.Mapping m = super.visitMapping(mapping, ctx);

                boolean matchingWorkflow = false;
                boolean argumentIsUsed = false;
                if (jobsMatcher.matches(getCursor().getParent().getParent())) {
                    Optional<Yaml.Mapping.Entry> usesEntry = m.getEntries()
                            .stream()
                            .filter(e -> e.getKey() instanceof Yaml.Scalar && "uses".equals((e.getKey().getValue())))
                            .findAny();
                    if (usesEntry.isPresent()) {
                        Yaml.Block usesValue = usesEntry.get().getValue();
                        if (usesValue instanceof Yaml.Scalar) {
                            if (((Yaml.Scalar) usesValue).getValue().equals(expectedReference)) {
                                matchingWorkflow = true;
                            }
                        }
                    }
                    Optional<Yaml.Mapping.Entry> withEntry = m.getEntries()
                            .stream()
                            .filter(e -> e.getKey() instanceof Yaml.Scalar && "with".equals((e.getKey().getValue())))
                            .findAny();
                    if (withEntry.isPresent()) {
                        Yaml.Block withValue = withEntry.get().getValue();
                        if (withValue instanceof Yaml.Mapping) {
                            Yaml.Mapping withMapping = (Yaml.Mapping) withValue;
                            argumentIsUsed = withMapping.getEntries()
                                    .stream()
                                    .anyMatch(e -> e.getKey() instanceof Yaml.Scalar && inputArgumentName.equals(e.getKey().getValue()));
                        }
                    }
                }
                if (matchingWorkflow && argumentIsUsed) {
                    return m.withEntries(ListUtils.map(m.getEntries(), entry -> {
                        if (entry.getKey() instanceof Yaml.Scalar && "with".equals(entry.getKey().getValue())) {
                            if (entry.getValue() instanceof Yaml.Mapping) {
                                Yaml.Mapping withMapping = (Yaml.Mapping) entry.getValue();
                                Yaml.Mapping newMapping = withMapping.withEntries(
                                    withMapping.getEntries().stream()
                                        .filter(e -> !(e.getKey() instanceof Yaml.Scalar && inputArgumentName.equals(e.getKey().getValue())))
                                        .collect(toList())
                                );
                                if (newMapping.getEntries().isEmpty()) {
                                    return null;
                                }
                                return entry.withValue(newMapping);
                            }
                        }
                        return entry;
                    }));
                }

                return m;
            }

        });
    }
}
