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
import org.openrewrite.github.IsGitHubActionsWorkflow;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

@Value
@EqualsAndHashCode(callSuper = false)
public class AnonymousJobsRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Find jobs without descriptive names";
    }

    @Override
    public String getDescription() {
        return "Find jobs that lack descriptive names, making them harder to identify in workflow runs. " +
                "Jobs without `name` properties default to their job ID, which may not be descriptive. " +
                "Based on [zizmor's anonymous-definition audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/anonymous_definition.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new AnonymousJobsVisitor()
        );
    }

    private static class AnonymousJobsVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            // Check if this entry is inside a "jobs" mapping
            if (isInsideJobsMapping() && mappingEntry.getValue() instanceof Yaml.Mapping) {
                Yaml.Mapping jobMapping = (Yaml.Mapping) mappingEntry.getValue();

                boolean hasName = jobMapping.getEntries().stream()
                        .anyMatch(jobProp -> {
                            if (jobProp.getKey() instanceof Yaml.Scalar) {
                                Yaml.Scalar scalar = (Yaml.Scalar) jobProp.getKey();
                                return "name".equals(scalar.getValue());
                            }
                            return false;
                        });

                // Skip reusable workflow calls (jobs that have "uses" instead of typical job properties)
                boolean isReusableWorkflowCall = jobMapping.getEntries().stream()
                        .anyMatch(jobProp -> {
                            if (jobProp.getKey() instanceof Yaml.Scalar) {
                                Yaml.Scalar scalar = (Yaml.Scalar) jobProp.getKey();
                                return "uses".equals(scalar.getValue());
                            }
                            return false;
                        });

                if (!hasName && !isReusableWorkflowCall) {
                    return SearchResult.found(mappingEntry,
                            "Job has no name. Add a descriptive name to make it easier to identify in workflow runs.");
                }
            }

            return mappingEntry;
        }

        private boolean isInsideJobsMapping() {
            // Check if this entry is directly under a "jobs" mapping
            // We want to avoid matching the "jobs" entry itself, only its children
            Cursor current = getCursor();
            Cursor parent = current.getParent();

            if (parent == null) {
                return false;
            }

            Object parentValue = parent.getValue();
            if (!(parentValue instanceof Yaml.Mapping)) {
                return false;
            }

            // Now check if this mapping is the value of a "jobs" entry
            Cursor grandParent = parent.getParent();
            if (grandParent == null) {
                return false;
            }

            Object grandParentValue = grandParent.getValue();
            if (grandParentValue instanceof Yaml.Mapping.Entry) {
                Yaml.Mapping.Entry grandParentEntry = (Yaml.Mapping.Entry) grandParentValue;
                if (grandParentEntry.getKey() instanceof Yaml.Scalar) {
                    Yaml.Scalar key = (Yaml.Scalar) grandParentEntry.getKey();
                    return "jobs".equals(key.getValue());
                }
            }

            return false;
        }
    }
}
