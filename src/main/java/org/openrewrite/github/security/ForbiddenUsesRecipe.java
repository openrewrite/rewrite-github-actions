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

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.github.IsGitHubActionsWorkflow;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class ForbiddenUsesRecipe extends Recipe {

    @Option(displayName = "Additional dangerous actions",
            description = "Additional actions to flag as dangerous, beyond the built-in list. " +
                    "These will be merged with the default dangerous actions.",
            required = false,
            example = "[\"some-org/dangerous-action@v1\", \"another-org/risky-action@v2\"]")
    @Nullable
    List<String> additionalDangerousActions;

    @Option(displayName = "Additional suspicious patterns",
            description = "Additional patterns to flag as suspicious, beyond the built-in patterns. " +
                    "These will be merged with the default suspicious patterns.",
            required = false,
            example = "[\"malware\", \"crypto-miner\", \"backdoor\"]")
    @Nullable
    List<String> additionalSuspiciousPatterns;

    private static final Set<String> KNOWN_DANGEROUS_ACTIONS = new HashSet<>(Arrays.asList(
            // Actions with known security issues or vulnerabilities
            "actions/checkout@v1",  // Has security vulnerabilities, should use v3+
            "actions/checkout@v2",  // Has security vulnerabilities, should use v3+
            "actions/setup-node@v1", // Deprecated with vulnerabilities
            "actions/setup-node@v2", // Deprecated with vulnerabilities
            // Add more known dangerous actions here
            "actions/cache@v1",      // Has vulnerabilities
            "actions/cache@v2"       // Has vulnerabilities
    ));

    private static final Set<String> SUSPICIOUS_ACTION_PATTERNS = new HashSet<>(Arrays.asList(
            // Actions that run arbitrary code
            "run",
            "exec",
            "eval",
            // Actions from suspicious organizations
            "malicious-org/",
            // Actions with suspicious names
            "download-and-run",
            "execute-script"
    ));

    Set<String> allDangerousActions;
    Set<String> allSuspiciousPatterns;

    public ForbiddenUsesRecipe() {
        this(null, null);
    }

    @JsonCreator
    public ForbiddenUsesRecipe(
            @Nullable List<String> additionalDangerousActions,
            @Nullable List<String> additionalSuspiciousPatterns) {
        this.additionalDangerousActions = additionalDangerousActions;
        this.additionalSuspiciousPatterns = additionalSuspiciousPatterns;

        // Merge static sets with provided options
        this.allDangerousActions = new HashSet<>(KNOWN_DANGEROUS_ACTIONS);
        if (additionalDangerousActions != null) {
            this.allDangerousActions.addAll(additionalDangerousActions);
        }

        this.allSuspiciousPatterns = new HashSet<>(SUSPICIOUS_ACTION_PATTERNS);
        if (additionalSuspiciousPatterns != null) {
            this.allSuspiciousPatterns.addAll(additionalSuspiciousPatterns);
        }
    }

    @Override
    public String getDisplayName() {
        return "Find forbidden action usage";
    }

    @Override
    public String getDescription() {
        return "Find usage of forbidden or dangerous GitHub Actions that have known " +
                "security vulnerabilities or follow suspicious patterns. " +
                "Based on [zizmor's forbidden-uses audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/forbidden_uses.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new ForbiddenUsesVisitor(allDangerousActions, allSuspiciousPatterns)
        );
    }

    private static class ForbiddenUsesVisitor extends YamlIsoVisitor<ExecutionContext> {

        private final Set<String> dangerousActions;
        private final Set<String> suspiciousPatterns;

        public ForbiddenUsesVisitor(Set<String> dangerousActions, Set<String> suspiciousPatterns) {
            this.dangerousActions = dangerousActions;
            this.suspiciousPatterns = suspiciousPatterns;
        }

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            if (isUsesEntry(mappingEntry)) {
                String usesValue = getUsesValue(mappingEntry);
                if (usesValue != null) {
                    String violation = checkForForbiddenAction(usesValue);
                    if (violation != null) {
                        return SearchResult.found(mappingEntry, violation);
                    }
                }
            }

            return mappingEntry;
        }

        private boolean isUsesEntry(Yaml.Mapping.Entry entry) {
            // Broader approach - match any "uses" entry and let the logic handle context validation
            return entry.getKey() instanceof Yaml.Scalar &&
                    "uses".equals(((Yaml.Scalar) entry.getKey()).getValue());
        }

        private @Nullable String getUsesValue(Yaml.Mapping.Entry entry) {
            if (entry.getValue() instanceof Yaml.Scalar) {
                return ((Yaml.Scalar) entry.getValue()).getValue();
            }
            return null;
        }

        private @Nullable String checkForForbiddenAction(String usesValue) {
            // Skip local actions and Docker actions
            if (usesValue.startsWith("./") || usesValue.startsWith("docker://")) {
                return null;
            }

            // Check against known dangerous actions
            for (String dangerous : dangerousActions) {
                if (usesValue.equals(dangerous)) {
                    return "Action '" + usesValue + "' is known to have security vulnerabilities. " +
                            "Consider upgrading to a more recent version or using an alternative.";
                }
            }

            // Check for suspicious patterns (longest match first to avoid partial matches)
            String longestMatch = null;
            for (String pattern : suspiciousPatterns) {
                if (usesValue.toLowerCase().contains(pattern.toLowerCase())) {
                    if (longestMatch == null || pattern.length() > longestMatch.length()) {
                        longestMatch = pattern;
                    }
                }
            }

            if (longestMatch != null) {
                return "Action '" + usesValue + "' contains suspicious pattern '" + longestMatch + "'. " +
                        "Review this action carefully for potential security risks.";
            }

            // Check for actions from unverified sources
            if (usesValue.contains("/") && !usesValue.startsWith("actions/")) {
                String[] parts = usesValue.split("/", 2);
                if (parts.length == 2) {
                    String owner = parts[0];
                    // Flag actions from single-character owners (often suspicious)
                    if (owner.length() == 1) {
                        return "Action '" + usesValue + "' is from a single-character organization '" +
                                owner + "' which may be suspicious. Verify the action's authenticity.";
                    }
                }
            }

            return null;
        }
    }
}
