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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class TemplateInjectionRecipe extends Recipe {

    // User-controllable contexts that can lead to injection vulnerabilities
    private static final Set<String> DANGEROUS_CONTEXTS = new HashSet<>(Arrays.asList(
            "github.event.pull_request.title",
            "github.event.pull_request.body",
            "github.event.pull_request.head.ref",
            "github.event.pull_request.head.label",
            "github.event.pull_request.head.repo.default_branch",
            "github.event.pull_request.base.ref",
            "github.event.issue.title",
            "github.event.issue.body",
            "github.event.comment.body",
            "github.event.review.body",
            "github.event.pages[0].page_name",
            "github.event.commits[0].message",
            "github.event.head_commit.message",
            "github.event.commits[0].author.name",
            "github.event.commits[0].author.email",
            "github.head_ref"
    ));

    // Contexts that reference user-controllable step outputs
    private static final Pattern STEPS_OUTPUT_PATTERN = Pattern.compile(
            "steps\\.[^.]+\\.outputs\\.[^\\s}]+"
    );

    // Actions known to have code injection sinks
    private static final Set<String> CODE_INJECTION_ACTIONS = new HashSet<>(Arrays.asList(
            "actions/github-script",
            "amadevus/pwsh-script",
            "jannekem/run-python-script-action",
            "cardinalby/js-eval-action"
    ));

    // Expression pattern to find GitHub Actions expressions
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile(
            "\\$\\{\\{([^}]+)\\}\\}"
    );

    @Override
    public String getDisplayName() {
        return "Find template injection vulnerabilities";
    }

    @Override
    public String getDescription() {
        return "Find GitHub Actions workflows vulnerable to template injection attacks. These occur when user-controllable " +
                "input (like pull request titles, issue bodies, or commit messages) is used directly in `run` commands or " +
                "`script` inputs without proper escaping. Attackers can exploit this to execute arbitrary code. " +
                "Based on [zizmor's `template-injection` audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/template_injection.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml"),
                new TemplateInjectionVisitor()
        );
    }

    private static class TemplateInjectionVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            // Check run commands for injection vulnerabilities
            if (isRunEntry(mappingEntry)) {
                return checkRunEntry(mappingEntry);
            }

            // Check uses entries for code injection actions
            if (isUsesEntry(mappingEntry)) {
                return checkUsesEntry(mappingEntry);
            }

            // Check script inputs for code injection actions
            if (isScriptEntry(mappingEntry)) {
                return checkScriptEntry(mappingEntry);
            }

            return mappingEntry;
        }

        private boolean isRunEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getKey() instanceof Yaml.Scalar)) {
                return false;
            }
            Yaml.Scalar key = (Yaml.Scalar) entry.getKey();
            return "run".equals(key.getValue());
        }

        private boolean isUsesEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getKey() instanceof Yaml.Scalar)) {
                return false;
            }
            Yaml.Scalar key = (Yaml.Scalar) entry.getKey();
            return "uses".equals(key.getValue());
        }

        private boolean isScriptEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getKey() instanceof Yaml.Scalar)) {
                return false;
            }
            Yaml.Scalar key = (Yaml.Scalar) entry.getKey();
            return "script".equals(key.getValue());
        }

        private Yaml.Mapping.Entry checkRunEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getValue() instanceof Yaml.Scalar)) {
                return entry;
            }

            String runCommand = ((Yaml.Scalar) entry.getValue()).getValue();

            // Check for dangerous template expressions in run commands
            String vulnerableContext = findVulnerableContext(runCommand);
            if (vulnerableContext != null) {
                String message;
                if (vulnerableContext.startsWith("User-controlled input")) {
                    message = "Potential template injection vulnerability. " + vulnerableContext + " used in run command without proper escaping.";
                } else {
                    message = "Potential template injection vulnerability. User-controlled input '" + vulnerableContext + "' used in run command without proper escaping.";
                }
                return SearchResult.found(entry, message);
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkUsesEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getValue() instanceof Yaml.Scalar)) {
                return entry;
            }

            String usesValue = ((Yaml.Scalar) entry.getValue()).getValue();

            // Check if this is a code injection action
            for (String dangerousAction : CODE_INJECTION_ACTIONS) {
                if (usesValue.startsWith(dangerousAction)) {
                    // Look for script input in the with section
                    if (hasVulnerableScriptInput()) {
                        return SearchResult.found(entry,
                                "Potential code injection in script input. User-controlled content in script execution context.");
                    }
                }
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkScriptEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getValue() instanceof Yaml.Scalar)) {
                return entry;
            }

            String scriptContent = ((Yaml.Scalar) entry.getValue()).getValue();

            // Check if we're inside a code injection action
            if (isInsideCodeInjectionAction()) {
                String vulnerableContext = findVulnerableContext(scriptContent);
                if (vulnerableContext != null) {
                    return SearchResult.found(entry,
                            "Potential code injection in script. User-controlled input '" + vulnerableContext +
                                    "' used in script without proper escaping.");
                }
            }

            return entry;
        }

        private String findVulnerableContext(String content) {
            // Find all expressions in the content
            java.util.regex.Matcher matcher = EXPRESSION_PATTERN.matcher(content);

            while (matcher.find()) {
                String expression = matcher.group(1).trim();

                // Check for complex expressions containing dangerous contexts first
                if (isComplexExpression(expression) && containsDangerousContextInExpression(expression)) {
                    return "User-controlled input in complex expression";
                }

                // Check for directly dangerous contexts
                for (String dangerousContext : DANGEROUS_CONTEXTS) {
                    if (expression.contains(dangerousContext)) {
                        return dangerousContext;
                    }
                }

                // Check for steps outputs (which may contain user input)
                java.util.regex.Matcher stepsMatcher = STEPS_OUTPUT_PATTERN.matcher(expression);
                if (stepsMatcher.find()) {
                    return stepsMatcher.group();
                }
            }

            return null;
        }

        private boolean isComplexExpression(String expression) {
            // Consider it complex if it contains function calls or multiple contexts
            return expression.contains("(") && expression.contains(")");
        }

        private boolean containsDangerousContextInExpression(String expression) {
            // Check for function calls or complex expressions that might contain dangerous contexts
            for (String dangerousContext : DANGEROUS_CONTEXTS) {
                if (expression.contains(dangerousContext)) {
                    return true;
                }
            }

            return false;
        }

        private boolean hasVulnerableScriptInput() {
            // Look for script input in the current step's with section
            Yaml.Mapping stepMapping = findParentStepMapping();
            if (stepMapping == null) {
                return false;
            }

            Yaml.Mapping withMapping = findWithMapping(stepMapping);
            if (withMapping == null) {
                return false;
            }

            // Check the script input for vulnerable contexts
            for (Yaml.Mapping.Entry withEntry : withMapping.getEntries()) {
                if (withEntry.getKey() instanceof Yaml.Scalar) {
                    Yaml.Scalar key = (Yaml.Scalar) withEntry.getKey();
                    if ("script".equals(key.getValue()) && withEntry.getValue() instanceof Yaml.Scalar) {
                        String scriptValue = ((Yaml.Scalar) withEntry.getValue()).getValue();
                        return findVulnerableContext(scriptValue) != null;
                    }
                }
            }

            return false;
        }

        private boolean isInsideCodeInjectionAction() {
            // Check if the current script entry is within a code injection action
            Yaml.Mapping stepMapping = findParentStepMapping();
            if (stepMapping == null) {
                return false;
            }

            // Look for uses entry in the step
            for (Yaml.Mapping.Entry stepEntry : stepMapping.getEntries()) {
                if (stepEntry.getKey() instanceof Yaml.Scalar) {
                    Yaml.Scalar key = (Yaml.Scalar) stepEntry.getKey();
                    if ("uses".equals(key.getValue()) && stepEntry.getValue() instanceof Yaml.Scalar) {
                        String usesValue = ((Yaml.Scalar) stepEntry.getValue()).getValue();
                        for (String dangerousAction : CODE_INJECTION_ACTIONS) {
                            if (usesValue.startsWith(dangerousAction)) {
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }

        private Yaml.Mapping findParentStepMapping() {
            // Walk up cursor to find the step mapping
            Cursor current = getCursor();
            while (current != null) {
                Object value = current.getValue();
                if (value instanceof Yaml.Mapping) {
                    Yaml.Mapping mapping = (Yaml.Mapping) value;
                    // Check if this mapping has 'uses' or 'run' (step indicators)
                    boolean isStep = mapping.getEntries().stream()
                            .anyMatch(mapEntry -> {
                                if (mapEntry.getKey() instanceof Yaml.Scalar) {
                                    Yaml.Scalar key = (Yaml.Scalar) mapEntry.getKey();
                                    return "uses".equals(key.getValue()) || "run".equals(key.getValue());
                                }
                                return false;
                            });

                    if (isStep) {
                        return mapping;
                    }
                }
                current = current.getParent();
            }
            return null;
        }

        private Yaml.Mapping findWithMapping(Yaml.Mapping stepMapping) {
            for (Yaml.Mapping.Entry stepEntry : stepMapping.getEntries()) {
                if (stepEntry.getKey() instanceof Yaml.Scalar) {
                    Yaml.Scalar key = (Yaml.Scalar) stepEntry.getKey();
                    if ("with".equals(key.getValue()) && stepEntry.getValue() instanceof Yaml.Mapping) {
                        return (Yaml.Mapping) stepEntry.getValue();
                    }
                }
            }
            return null;
        }
    }
}
