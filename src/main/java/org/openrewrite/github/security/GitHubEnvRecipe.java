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
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class GitHubEnvRecipe extends Recipe {

    // Dangerous triggers that make GITHUB_ENV usage risky
    private static final Set<String> DANGEROUS_TRIGGERS = new HashSet<>(Arrays.asList(
            "pull_request_target",
            "workflow_run"
    ));

    // Pattern to detect GITHUB_ENV and GITHUB_PATH usage in various shells
    private static final Pattern GITHUB_ENV_WRITE_PATTERN = Pattern.compile(
            "(?i)(>>?\\s*[\"']?\\$\\{?GITHUB_ENV\\}?[\"']?|" +        // bash: >> $GITHUB_ENV
                    ">>?\\s*[\"']?%GITHUB_ENV%[\"']?|" +                     // cmd: >> %GITHUB_ENV%
                    ">>?\\s*[\"']?\\$env:GITHUB_ENV[\"']?|" +                // pwsh: >> $env:GITHUB_ENV
                    "Out-File.*\\$env:GITHUB_ENV|" +                         // pwsh: Out-File
                    "Add-Content.*\\$env:GITHUB_ENV|" +                      // pwsh: Add-Content
                    "Set-Content.*\\$env:GITHUB_ENV|" +                      // pwsh: Set-Content
                    "Tee-Object.*\\$env:GITHUB_ENV|" +                       // pwsh: Tee-Object
                    "\\|\\s*tee\\s+[\"']?\\$\\{?GITHUB_ENV\\}?[\"']?)" +     // bash: | tee $GITHUB_ENV
                    "|GITHUB_PATH"                                            // Any GITHUB_PATH usage
    );

    // Pattern to detect simple static content (likely safe)
    private static final Pattern STATIC_ECHO_PATTERN = Pattern.compile(
            "^\\s*echo\\s+[\"']?[^$`]*[\"']?\\s*>>", Pattern.MULTILINE
    );

    String displayName = "Find dangerous GITHUB_ENV usage";

    String description = "Detects dangerous usage of `GITHUB_ENV` and `GITHUB_PATH` environment files in workflows with " +
                "risky triggers like `pull_request_target` or `workflow_run`. Writing to these files can " +
                "allow code injection when the content includes user-controlled data. " +
                "Based on [zizmor's github-env audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/github_env.rs).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsWorkflow(), new YamlIsoVisitor<ExecutionContext>() {
            private boolean hasDangerousTriggers = false;

            @Override
            public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                // Reset state for each document
                hasDangerousTriggers = false;

                // First pass: check if workflow has dangerous triggers
                analyzeTriggers(document);

                // Only analyze run steps if we have dangerous triggers
                if (hasDangerousTriggers) {
                    return super.visitDocument(document, ctx);
                }

                return document;
            }

            private void analyzeTriggers(Yaml.Document document) {
                if (document.getBlock() instanceof Yaml.Mapping) {
                    Yaml.Mapping workflowMapping = (Yaml.Mapping) document.getBlock();

                    for (Yaml.Mapping.Entry entry : workflowMapping.getEntries()) {
                        if (entry.getKey() instanceof Yaml.Scalar && "on".equals(((Yaml.Scalar) entry.getKey()).getValue())) {
                            hasDangerousTriggers = checkForDangerousTriggers(entry.getValue());
                            break;
                        }
                    }
                }
            }

            private boolean checkForDangerousTriggers(Yaml.Block onValue) {
                String scalarTrigger = YamlHelper.getScalarValue(onValue);
                if (scalarTrigger != null) {
                    return DANGEROUS_TRIGGERS.contains(scalarTrigger);
                }
                if (onValue instanceof Yaml.Sequence) {
                    Yaml.Sequence sequence = (Yaml.Sequence) onValue;
                    for (Yaml.Sequence.Entry seqEntry : sequence.getEntries()) {
                        String trigger = YamlHelper.getScalarValue(seqEntry.getBlock());
                        if (trigger != null && DANGEROUS_TRIGGERS.contains(trigger)) {
                            return true;
                        }
                    }
                } else if (onValue instanceof Yaml.Mapping) {
                    Yaml.Mapping mapping = (Yaml.Mapping) onValue;
                    for (Yaml.Mapping.Entry triggerEntry : mapping.getEntries()) {
                        String trigger = triggerEntry.getKey().getValue();
                        if (DANGEROUS_TRIGGERS.contains(trigger)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

                // Only check if we have dangerous triggers
                if (!hasDangerousTriggers) {
                    return mappingEntry;
                }

                // Look for run steps that write to GITHUB_ENV or GITHUB_PATH
                if (isRunStepEntry(mappingEntry)) {
                    String runContent = getRunContent(mappingEntry);
                    if (runContent != null && usesGitHubEnv(runContent)) {
                        String envVar = getEnvironmentVariable(runContent);
                        return SearchResult.found(mappingEntry,
                                String.format("Write to %s may allow code execution in a workflow with dangerous triggers. " +
                                        "This can lead to code injection when the written content includes user-controlled data. " +
                                        "Ensure any dynamic content is properly sanitized or avoid writing to environment files " +
                                        "in workflows triggered by untrusted events.", envVar));
                    }
                }

                return mappingEntry;
            }

            private boolean isRunStepEntry(Yaml.Mapping.Entry entry) {
                return entry.getKey() instanceof Yaml.Scalar && "run".equals(((Yaml.Scalar) entry.getKey()).getValue());
            }

            private String getRunContent(Yaml.Mapping.Entry entry) {
                return YamlHelper.getScalarValue(entry.getValue());
            }

            private boolean usesGitHubEnv(String runContent) {
                // Check if the run content writes to GITHUB_ENV or GITHUB_PATH
                if (!GITHUB_ENV_WRITE_PATTERN.matcher(runContent).find()) {
                    return false;
                }

                // If it's just static echo content, it's likely safe
                if (isStaticEcho(runContent)) {
                    return false;
                }

                return true;
            }

            private boolean isStaticEcho(String runContent) {
                // Simple heuristic: if all GITHUB_ENV writes are from static echo commands, consider it safe
                // This is a simplified version of the complex tree-sitter analysis in zizmor
                return STATIC_ECHO_PATTERN.matcher(runContent).find() &&
                        !runContent.contains("$") &&
                        !runContent.contains("`") &&
                        !runContent.contains("$(");
            }

            private String getEnvironmentVariable(String runContent) {
                if (runContent.toUpperCase().contains("GITHUB_ENV")) {
                    return "GITHUB_ENV";
                }
                if (runContent.toUpperCase().contains("GITHUB_PATH")) {
                    return "GITHUB_PATH";
                }
                return "environment file";
            }
        });
    }

}
