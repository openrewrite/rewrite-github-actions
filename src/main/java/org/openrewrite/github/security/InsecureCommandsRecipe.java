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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.github.IsGitHubActionsWorkflow;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

@Value
@EqualsAndHashCode(callSuper = false)
public class InsecureCommandsRecipe extends Recipe {

    private static final String INSECURE_COMMANDS_VAR = "ACTIONS_ALLOW_UNSECURE_COMMANDS";

    String displayName = "Find insecure commands configuration";

    String description = "Detects when insecure workflow commands are enabled via `ACTIONS_ALLOW_UNSECURE_COMMANDS`. " +
                "This environment variable enables dangerous workflow commands that can lead to code injection vulnerabilities. " +
                "Based on [zizmor's insecure-commands audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/insecure_commands.rs).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsWorkflow(), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

                // Look for ACTIONS_ALLOW_UNSECURE_COMMANDS - simple pattern matching
                if (isInsecureCommandsEntry(mappingEntry)) {
                    String value = getEnvironmentValue(mappingEntry);
                    if (value != null && isTruthyValue(value)) {
                        return SearchResult.found(mappingEntry,
                                "Insecure commands are enabled via ACTIONS_ALLOW_UNSECURE_COMMANDS. " +
                                        "This allows dangerous workflow commands that can lead to code injection. " +
                                        "Remove this environment variable to disable insecure commands.");
                    }
                }

                return mappingEntry;
            }

            private boolean isInsecureCommandsEntry(Yaml.Mapping.Entry entry) {
                if (!(entry.getKey() instanceof Yaml.Scalar)) {
                    return false;
                }

                String key = ((Yaml.Scalar) entry.getKey()).getValue();
                return INSECURE_COMMANDS_VAR.equals(key);
            }

            private String getEnvironmentValue(Yaml.Mapping.Entry entry) {
                if (entry.getValue() instanceof Yaml.Scalar) {
                    return ((Yaml.Scalar) entry.getValue()).getValue();
                }
                return null;
            }

            private boolean isTruthyValue(String value) {
                if (value == null) {
                    return false;
                }

                String lowerValue = value.toLowerCase().trim();
                // Check various truthy representations
                return "true".equals(lowerValue) ||
                        "1".equals(lowerValue) ||
                        "yes".equals(lowerValue) ||
                        "on".equals(lowerValue);
            }
        });
    }

}
