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
import org.openrewrite.*;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.time.Duration;

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
        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);

                // Check if this is a "uses" entry
                if (e.getKey() instanceof Yaml.Scalar && "uses".equals((e.getKey()).getValue())) {
                    if (e.getValue() instanceof Yaml.Scalar) {
                        String usesValue = ((Yaml.Scalar) e.getValue()).getValue();

                        // Check if this matches our workflow reference and version
                        String expectedReference = workflowReference + "@" + version;
                        if (usesValue.equals(expectedReference)) {
                            // We found a matching workflow call
                            // Now we need to remove the input argument from the "with" section
                            getCursor().putMessage("REMOVE_INPUT", true);
                        }
                    }
                }

                // Check if this is a "with" entry and we're in a matching workflow call
                if (e.getKey() instanceof Yaml.Scalar && "with".equals((e.getKey()).getValue())) {
                    Boolean shouldRemoveInput = getCursor().getNearestMessage("REMOVE_INPUT");
                    if (Boolean.TRUE.equals(shouldRemoveInput)) {
                        if (e.getValue() instanceof Yaml.Mapping) {
                            Yaml.Mapping withMapping = (Yaml.Mapping) e.getValue();
                            Yaml.Mapping newMapping = withMapping;

                            // Remove the specific input argument
                            for (Yaml.Mapping.Entry withEntry : withMapping.getEntries()) {
                                if (withEntry.getKey() instanceof Yaml.Scalar && inputArgumentName.equals((withEntry.getKey()).getValue())) {
                                    newMapping = newMapping.withEntries(
                                        withMapping.getEntries().stream()
                                            .filter(we -> we != withEntry)
                                            .collect(java.util.stream.Collectors.toList())
                                    );
                                    break;
                                }
                            }

                            if (newMapping != withMapping) {
                                return e.withValue(newMapping);
                            }
                        }
                    }
                }

                return e;
            }

            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                Yaml.Mapping m = super.visitMapping(mapping, ctx);

                // Clear the message after processing the job or step containing the workflow call
                getCursor().pollNearestMessage("REMOVE_INPUT");

                return m;
            }
        });
    }
}
