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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class DangerousTriggersRecipe extends Recipe {

    private static final Set<String> DANGEROUS_TRIGGERS = new HashSet<>(Arrays.asList(
            "pull_request_target",
            "workflow_run"
    ));

    String displayName = "Find dangerous workflow triggers";

    String description = "Detects use of fundamentally insecure workflow triggers like `pull_request_target` and `workflow_run`. " +
                "These triggers run with elevated privileges and are almost always used insecurely, " +
                "potentially allowing code injection from untrusted sources. " +
                "Based on [zizmor's dangerous-triggers audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/dangerous_triggers.rs).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsWorkflow(), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

                // Look for "on" key - simple pattern matching
                if (isOnEntry(mappingEntry)) {
                    return checkTriggersInOnEntry(mappingEntry);
                }

                return mappingEntry;
            }

            private boolean isOnEntry(Yaml.Mapping.Entry entry) {
                if (!(entry.getKey() instanceof Yaml.Scalar)) {
                    return false;
                }

                String key = ((Yaml.Scalar) entry.getKey()).getValue();
                return "on".equals(key);
            }

            private Yaml.Mapping.Entry checkTriggersInOnEntry(Yaml.Mapping.Entry onEntry) {
                Yaml.Block onValue = onEntry.getValue();

                if (onValue instanceof Yaml.Scalar) {
                    // Single trigger: "on: push"
                    String trigger = ((Yaml.Scalar) onValue).getValue();
                    if (DANGEROUS_TRIGGERS.contains(trigger)) {
                        String message = getDangerousTriggersMessage(trigger);
                        return SearchResult.found(onEntry, message);
                    }
                } else if (onValue instanceof Yaml.Sequence) {
                    // Array of triggers: "on: [push, pull_request]"
                    Yaml.Sequence sequence = (Yaml.Sequence) onValue;
                    for (Yaml.Sequence.Entry seqEntry : sequence.getEntries()) {
                        if (seqEntry.getBlock() instanceof Yaml.Scalar) {
                            String trigger = ((Yaml.Scalar) seqEntry.getBlock()).getValue();
                            if (DANGEROUS_TRIGGERS.contains(trigger)) {
                                String message = getDangerousTriggersMessage(trigger);
                                return SearchResult.found(onEntry, message);
                            }
                        }
                    }
                } else if (onValue instanceof Yaml.Mapping) {
                    // Object of triggers: "on: { push: {...}, pull_request: {...} }"
                    Yaml.Mapping mapping = (Yaml.Mapping) onValue;
                    for (Yaml.Mapping.Entry triggerEntry : mapping.getEntries()) {
                        if (triggerEntry.getKey() instanceof Yaml.Scalar) {
                            String trigger = ((Yaml.Scalar) triggerEntry.getKey()).getValue();
                            if (DANGEROUS_TRIGGERS.contains(trigger)) {
                                String message = getDangerousTriggersMessage(trigger);
                                return SearchResult.found(onEntry, message);
                            }
                        }
                    }
                }

                return onEntry;
            }

            private String getDangerousTriggersMessage(String trigger) {
                switch (trigger) {
                    case "pull_request_target":
                        return "The 'pull_request_target' trigger is almost always used insecurely. " +
                                "It runs with write permissions in the context of the target repository, " +
                                "potentially allowing code injection from pull requests. " +
                                "Consider using 'pull_request' instead, or implement proper isolation.";
                    case "workflow_run":
                        return "The 'workflow_run' trigger is almost always used insecurely. " +
                                "It can trigger workflows with sensitive permissions based on external events. " +
                                "Consider using more specific triggers with explicit safety checks.";
                    default:
                        return String.format("The '%s' trigger is considered insecure and should be avoided.", trigger);
                }
            }
        });
    }

}
