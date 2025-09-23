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
public class TrustedPublishingRecipe extends Recipe {

    private static final Set<String> KNOWN_PYTHON_TP_REGISTRIES = new HashSet<>(Arrays.asList(
        "https://upload.pypi.org/legacy/",
        "https://test.pypi.org/legacy/"
    ));

    private static final Set<String> KNOWN_RUBY_TP_REGISTRIES = new HashSet<>(Arrays.asList(
        "https://rubygems.org"
    ));

    private static final Set<String> KNOWN_NPM_TP_REGISTRIES = new HashSet<>(Arrays.asList(
        "https://registry.npmjs.org"
    ));

    // Regex patterns for manual publishing commands
    private static final Pattern[] MANUAL_PUBLISH_PATTERNS = {
        Pattern.compile("(?s)twine\\s+(.+\\s+)?upload"),
        Pattern.compile("(?s)cargo\\s+(.+\\s+)?publish"),
        Pattern.compile("(?s)npm\\s+(.+\\s+)?publish"),
        Pattern.compile("(?s)yarn\\s+(.+\\s+)?npm\\s+publish"),
        Pattern.compile("(?s)pnpm\\s+(.+\\s+)?publish"),
        Pattern.compile("(?s)gem\\s+(.+\\s+)?push"),
        Pattern.compile("(?s)uv\\s+(.+\\s+)?publish"),
        Pattern.compile("(?s)hatch\\s+(.+\\s+)?publish"),
        Pattern.compile("(?s)pdm\\s+(.+\\s+)?publish")
    };

    @Override
    public String getDisplayName() {
        return "Find manual credentials instead of trusted publishing";
    }

    @Override
    public String getDescription() {
        return "Find workflows that use manual credentials for publishing instead of OIDC trusted publishing. " +
               "Trusted publishing eliminates the need for long-lived API tokens and provides better security " +
               "through short-lived, automatically-rotated tokens. " +
               "Based on [zizmor's use-trusted-publishing audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/use_trusted_publishing.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
            new FindSourceFiles(".github/workflows/*.yml"),
            new TrustedPublishingVisitor()
        );
    }

    private static class TrustedPublishingVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            // Check for problematic actions
            if (isUsesEntry(mappingEntry)) {
                return checkUsesEntry(mappingEntry);
            }

            // Check for problematic run commands
            if (isRunEntry(mappingEntry)) {
                return checkRunEntry(mappingEntry);
            }

            // Check for problematic with entries
            if (isWithinProblematicAction()) {
                return checkWithEntry(mappingEntry);
            }

            return mappingEntry;
        }

        private boolean isUsesEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getKey() instanceof Yaml.Scalar)) {
                return false;
            }
            Yaml.Scalar key = (Yaml.Scalar) entry.getKey();
            return "uses".equals(key.getValue());
        }

        private boolean isRunEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getKey() instanceof Yaml.Scalar)) {
                return false;
            }
            Yaml.Scalar key = (Yaml.Scalar) entry.getKey();
            return "run".equals(key.getValue());
        }

        private Yaml.Mapping.Entry checkUsesEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getValue() instanceof Yaml.Scalar)) {
                return entry;
            }

            String usesValue = ((Yaml.Scalar) entry.getValue()).getValue();

            // Check for known problematic publishing actions
            if (usesValue.startsWith( "pypa/gh-action-pypi-publish" )) {
                return checkPyPIAction( entry );
            }
            if (usesValue.startsWith( "rubygems/release-gem" )) {
                return checkRubyGemsAction( entry );
            }
            if (usesValue.startsWith( "rubygems/configure-rubygems-credentials" )) {
                return checkRubyGemsCredentialsAction( entry );
            }
            if (usesValue.startsWith( "actions/setup-node" )) {
                return checkSetupNodeAction( entry );
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkPyPIAction(Yaml.Mapping.Entry entry) {
            // Look for 'with' section in the parent step
            Yaml.Mapping stepMapping = findParentStepMapping();
            if (stepMapping == null) {
                return entry;
            }

            Yaml.Mapping withMapping = findWithMapping(stepMapping);
            if (withMapping == null) {
                return entry;
            }

            // Check if it has password but is publishing to a trusted registry
            boolean hasPassword = withMapping.getEntries().stream()
                .anyMatch(withEntry -> {
                    if (withEntry.getKey() instanceof Yaml.Scalar) {
                        Yaml.Scalar key = (Yaml.Scalar) withEntry.getKey();
                        return "password".equals(key.getValue());
                    }
                    return false;
                });

            boolean isTrustedRegistry = withMapping.getEntries().stream()
                .anyMatch(withEntry -> {
                    if (withEntry.getKey() instanceof Yaml.Scalar) {
                        Yaml.Scalar key = (Yaml.Scalar) withEntry.getKey();
                        if ("repository-url".equals(key.getValue()) || "repository_url".equals(key.getValue())) {
                            if (withEntry.getValue() instanceof Yaml.Scalar) {
                                String repoUrl = ((Yaml.Scalar) withEntry.getValue()).getValue();
                                return KNOWN_PYTHON_TP_REGISTRIES.contains(repoUrl);
                            }
                        }
                    }
                    return false;
                });

            if (hasPassword && isTrustedRegistry) {
                return SearchResult.found(entry,
                    "Uses manual credentials instead of trusted publishing. Consider using OIDC trusted publishing for better security.");
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkRubyGemsAction(Yaml.Mapping.Entry entry) {
            Yaml.Mapping stepMapping = findParentStepMapping();
            if (stepMapping == null) {
                return entry;
            }

            Yaml.Mapping withMapping = findWithMapping(stepMapping);
            if (withMapping == null) {
                return entry;
            }

            // Check if setup-trusted-publisher is explicitly false
            boolean explicitlyDisabled = withMapping.getEntries().stream()
                .anyMatch(withEntry -> {
                    if (withEntry.getKey() instanceof Yaml.Scalar) {
                        Yaml.Scalar key = (Yaml.Scalar) withEntry.getKey();
                        if ("setup-trusted-publisher".equals(key.getValue())) {
                            if (withEntry.getValue() instanceof Yaml.Scalar) {
                                String value = ((Yaml.Scalar) withEntry.getValue()).getValue();
                                return "false".equals(value);
                            }
                        }
                    }
                    return false;
                });

            if (explicitlyDisabled) {
                return SearchResult.found(entry,
                    "Uses manual credentials instead of trusted publishing. Consider using OIDC trusted publishing for better security.");
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkRubyGemsCredentialsAction(Yaml.Mapping.Entry entry) {
            Yaml.Mapping stepMapping = findParentStepMapping();
            if (stepMapping == null) {
                return entry;
            }

            Yaml.Mapping withMapping = findWithMapping(stepMapping);
            if (withMapping == null) {
                return entry;
            }

            // Check if it has api-token and gem-server for rubygems
            boolean hasApiToken = withMapping.getEntries().stream()
                .anyMatch(withEntry -> {
                    if (withEntry.getKey() instanceof Yaml.Scalar) {
                        Yaml.Scalar key = (Yaml.Scalar) withEntry.getKey();
                        return "api-token".equals(key.getValue());
                    }
                    return false;
                });

            boolean isRubyGemsServer = withMapping.getEntries().stream()
                .anyMatch(withEntry -> {
                    if (withEntry.getKey() instanceof Yaml.Scalar) {
                        Yaml.Scalar key = (Yaml.Scalar) withEntry.getKey();
                        if ("gem-server".equals(key.getValue())) {
                            if (withEntry.getValue() instanceof Yaml.Scalar) {
                                String server = ((Yaml.Scalar) withEntry.getValue()).getValue();
                                return KNOWN_RUBY_TP_REGISTRIES.contains(server);
                            }
                        }
                    }
                    return false;
                });

            if (hasApiToken && isRubyGemsServer) {
                return SearchResult.found(entry,
                    "Uses manual credentials instead of trusted publishing. Consider using OIDC trusted publishing for better security.");
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkSetupNodeAction(Yaml.Mapping.Entry entry) {
            Yaml.Mapping stepMapping = findParentStepMapping();
            if (stepMapping == null) {
                return entry;
            }

            Yaml.Mapping withMapping = findWithMapping(stepMapping);
            if (withMapping == null) {
                return entry;
            }

            // Check if it has registry-url for npmjs and always-auth is true
            boolean isNpmRegistry = withMapping.getEntries().stream()
                .anyMatch(withEntry -> {
                    if (withEntry.getKey() instanceof Yaml.Scalar) {
                        Yaml.Scalar key = (Yaml.Scalar) withEntry.getKey();
                        if ("registry-url".equals(key.getValue())) {
                            if (withEntry.getValue() instanceof Yaml.Scalar) {
                                String registryUrl = ((Yaml.Scalar) withEntry.getValue()).getValue();
                                return KNOWN_NPM_TP_REGISTRIES.contains(registryUrl);
                            }
                        }
                    }
                    return false;
                });

            boolean hasAlwaysAuth = withMapping.getEntries().stream()
                .anyMatch(withEntry -> {
                    if (withEntry.getKey() instanceof Yaml.Scalar) {
                        Yaml.Scalar key = (Yaml.Scalar) withEntry.getKey();
                        if ("always-auth".equals(key.getValue())) {
                            if (withEntry.getValue() instanceof Yaml.Scalar) {
                                String value = ((Yaml.Scalar) withEntry.getValue()).getValue();
                                return "true".equals(value);
                            }
                        }
                    }
                    return false;
                });

            if (isNpmRegistry && hasAlwaysAuth) {
                return SearchResult.found(entry,
                    "Uses manual credentials instead of trusted publishing. Consider using OIDC trusted publishing for better security.");
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkRunEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getValue() instanceof Yaml.Scalar)) {
                return entry;
            }

            String runCommand = ((Yaml.Scalar) entry.getValue()).getValue();

            for (Pattern pattern : MANUAL_PUBLISH_PATTERNS) {
                if (pattern.matcher(runCommand).find()) {
                    return SearchResult.found(entry,
                        "Manual publishing command detected. Consider using trusted publishing actions instead.");
                }
            }

            return entry;
        }

        private Yaml.Mapping findParentStepMapping() {
            // Walk up cursor to find the step mapping that contains this 'uses' entry
            Cursor current = getCursor();
            while (current != null) {
                Object value = current.getValue();
                if (value instanceof Yaml.Mapping) {
                    Yaml.Mapping mapping = (Yaml.Mapping) value;
                    // Check if this mapping has both 'uses' and potentially 'with'
                    boolean hasUses = mapping.getEntries().stream()
                        .anyMatch(mapEntry -> {
                            if (mapEntry.getKey() instanceof Yaml.Scalar) {
                                Yaml.Scalar key = (Yaml.Scalar) mapEntry.getKey();
                                return "uses".equals(key.getValue());
                            }
                            return false;
                        });

                    if (hasUses) {
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

        private boolean isWithinProblematicAction() {
            // Check if we're inside a 'with' mapping of a problematic action
            Cursor current = getCursor();

            // First, check if we're inside a 'with' mapping
            while (current != null) {
                Object value = current.getValue();
                if (value instanceof Yaml.Mapping.Entry) {
                    Yaml.Mapping.Entry parentEntry = (Yaml.Mapping.Entry) value;
                    if (parentEntry.getKey() instanceof Yaml.Scalar) {
                        Yaml.Scalar key = (Yaml.Scalar) parentEntry.getKey();
                        if ("with".equals(key.getValue())) {
                            // Now check if the parent step uses a problematic action
                            return isProblematicActionStep(current);
                        }
                    }
                }
                current = current.getParent();
            }
            return false;
        }

        private boolean isProblematicActionStep(Cursor withCursor) {
            // Go up to find the step mapping
            Cursor stepCursor = withCursor.getParent();
            while (stepCursor != null) {
                Object value = stepCursor.getValue();
                if (value instanceof Yaml.Mapping) {
                    Yaml.Mapping stepMapping = (Yaml.Mapping) value;

                    // Look for uses entry
                    for (Yaml.Mapping.Entry stepEntry : stepMapping.getEntries()) {
                        if (stepEntry.getKey() instanceof Yaml.Scalar) {
                            Yaml.Scalar key = (Yaml.Scalar) stepEntry.getKey();
                            if ("uses".equals(key.getValue()) && stepEntry.getValue() instanceof Yaml.Scalar) {
                                String usesValue = ((Yaml.Scalar) stepEntry.getValue()).getValue();
                                return usesValue.startsWith("pypa/gh-action-pypi-publish") ||
                                       usesValue.startsWith("rubygems/release-gem") ||
                                       usesValue.startsWith("rubygems/configure-rubygems-credentials") ||
                                       usesValue.startsWith("actions/setup-node");
                            }
                        }
                    }
                }
                stepCursor = stepCursor.getParent();
            }
            return false;
        }

        private Yaml.Mapping.Entry checkWithEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getKey() instanceof Yaml.Scalar)) {
                return entry;
            }

            Yaml.Scalar key = (Yaml.Scalar) entry.getKey();
            String keyValue = key.getValue();

            // Check for problematic keys
            if ("password".equals(keyValue) ||
                "setup-trusted-publisher".equals(keyValue) ||
                "always-auth".equals(keyValue) ||
                "api-token".equals(keyValue)) {

                // Verify the value indicates manual credentials
                if (entry.getValue() instanceof Yaml.Scalar) {
                    String value = ((Yaml.Scalar) entry.getValue()).getValue();

                    // Flag password entries
                    if ("password".equals(keyValue)) {
                        return SearchResult.found(entry, "Manual credential used here");
                    }

                    // Flag setup-trusted-publisher: false
                    if ("setup-trusted-publisher".equals(keyValue) && "false".equals(value)) {
                        return SearchResult.found(entry, "Manual credential used here");
                    }

                    // Flag always-auth: true for npm
                    if ("always-auth".equals(keyValue) && "true".equals(value)) {
                        return SearchResult.found(entry, "Manual credential used here");
                    }

                    // Flag api-token entries
                    if ("api-token".equals(keyValue)) {
                        return SearchResult.found(entry, "Manual credential used here");
                    }
                }
            }

            return entry;
        }
    }
}
