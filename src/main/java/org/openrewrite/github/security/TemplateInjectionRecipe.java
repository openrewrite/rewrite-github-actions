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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.github.util.YamlScalarAccessor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
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

    private static class TemplateInjectionVisitor extends YamlIsoVisitor<ExecutionContext> implements YamlScalarAccessor {

        private static final JsonPathMatcher STEP_RUN_MATCHER = new JsonPathMatcher("$..steps[*].run");
        private static final JsonPathMatcher STEP_USES_MATCHER = new JsonPathMatcher("$..steps[*].uses");
        private static final JsonPathMatcher STEP_SCRIPT_MATCHER = new JsonPathMatcher("$..steps[*].with.script");

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            // Check run commands for injection vulnerabilities
            if (STEP_RUN_MATCHER.matches(getCursor())) {
                return checkRunEntry(mappingEntry);
            }

            // Check uses entries for code injection actions
            if (STEP_USES_MATCHER.matches(getCursor())) {
                return checkUsesEntry(mappingEntry);
            }

            // Check script inputs for code injection actions
            if (STEP_SCRIPT_MATCHER.matches(getCursor())) {
                return checkScriptEntry(mappingEntry);
            }

            return mappingEntry;
        }

        private Yaml.Mapping.Entry checkRunEntry(Yaml.Mapping.Entry entry) {
            String runCommand = getScalarValue(entry.getValue());
            if (runCommand == null) {
                return entry;
            }

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
            String usesValue = getScalarValue(entry.getValue());
            if (usesValue == null) {
                return entry;
            }

            // Check if this is a code injection action
            for (String dangerousAction : CODE_INJECTION_ACTIONS) {
                if (usesValue.startsWith(dangerousAction)) {
                    // Flag the use of a code injection action as potentially dangerous
                    return SearchResult.found(entry,
                            "Potential code injection in script input. User-controlled content in script execution context.");
                }
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkScriptEntry(Yaml.Mapping.Entry entry) {
            String scriptContent = getScalarValue(entry.getValue());
            if (scriptContent == null) {
                return entry;
            }

            // Check for vulnerable contexts in the script content
            String vulnerableContext = findVulnerableContext(scriptContent);
            if (vulnerableContext != null) {
                String message;
                if (vulnerableContext.startsWith("User-controlled input")) {
                    message = "Potential code injection in script. " + vulnerableContext + " used in script without proper escaping.";
                } else {
                    message = "Potential code injection in script. User-controlled input '" + vulnerableContext +
                            "' used in script without proper escaping.";
                }
                return SearchResult.found(entry, message);
            }

            return entry;
        }

        private @Nullable String findVulnerableContext(String content) {
            // Find all expressions in the content
            Matcher matcher = EXPRESSION_PATTERN.matcher(content);

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
                Matcher stepsMatcher = STEPS_OUTPUT_PATTERN.matcher(expression);
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

    }
}
