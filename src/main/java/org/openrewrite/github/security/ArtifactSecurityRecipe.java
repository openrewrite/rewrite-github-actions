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
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.concurrent.atomic.AtomicBoolean;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class ArtifactSecurityRecipe extends Recipe {

    // Dangerous paths that may contain credentials or sensitive information
    private static final Set<String> DANGEROUS_PATHS = new HashSet<>(Arrays.asList(
            "~/.ssh/", "~/.ssh", ".ssh/", ".ssh",
            "~/.aws/", "~/.aws", ".aws/", ".aws",
            "~/.docker/", "~/.docker", ".docker/", ".docker",
            "~/.kube/", "~/.kube", ".kube/", ".kube",
            "~/.config/", "~/.config",
            "~/.gitconfig", ".gitconfig",
            "~/.npmrc", ".npmrc",
            "~/.pypirc", ".pypirc",
            "/etc/passwd", "/etc/shadow", "/etc/hosts",
            "/var/log/", "/var/log",
            "/tmp/", "/tmp",
            "/root/", "/root",
            "~/.bash_history", ".bash_history",
            "~/.zsh_history", ".zsh_history",
            "/home/", "/home"
    ));

    // Path patterns that are likely dangerous
    private static final String[] DANGEROUS_PATTERNS = {
            "config.json", "credentials", "token", "secret", "key", "password", "passwd"
    };

    @Override
    public String getDisplayName() {
        return "Find credential persistence through GitHub Actions artifacts";
    }

    @Override
    public String getDescription() {
        return "Find workflows that may persist credentials through artifact uploads. This occurs when checkout " +
                "actions don't disable credential persistence and upload actions include sensitive paths that may " +
                "contain credentials, SSH keys, or configuration files. " +
                "Based on [zizmor's `artipacked` audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/artipacked.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml"),
                new ArtifactSecurityVisitor()
        );
    }

    private static class ArtifactSecurityVisitor extends YamlIsoVisitor<ExecutionContext> {

        private static final JsonPathMatcher USES_MATCHER = new JsonPathMatcher("$.jobs.*.steps[*].uses");

        @Override
        public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
            // Analyze the entire workflow for credential persistence patterns
            Yaml.Document d = super.visitDocument(document, ctx);

            // Check if this workflow has both vulnerable checkouts and uploads
            if (d.getBlock() instanceof Yaml.Mapping) {
                checkWorkflowForCredentialPersistence((Yaml.Mapping) d.getBlock());
            }

            return d;
        }

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            if ("uses".equals(mappingEntry.getKey().getValue())) {
                return checkUsesEntry(mappingEntry);
            }

            return mappingEntry;
        }


        private Yaml.Mapping.Entry checkUsesEntry(Yaml.Mapping.Entry entry) {
            String usesValue = entry.getValue() instanceof Yaml.Scalar ? ((Yaml.Scalar) entry.getValue()).getValue() : null;
            if (usesValue == null) {
                return entry;
            }

            // Check for checkout actions
            if (usesValue.startsWith("actions/checkout")) {
                return checkCheckoutAction(entry);
            }

            // Check for upload-artifact actions
            if (usesValue.startsWith("actions/upload-artifact")) {
                return checkUploadArtifactAction(entry);
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkCheckoutAction(Yaml.Mapping.Entry entry) {
            // Look for 'with' section in the parent step
            Yaml.Mapping stepMapping = findParentStepMapping();
            if (stepMapping == null) {
                return entry;
            }

            Yaml.Mapping withMapping = findWithMapping(stepMapping);

            if (withMapping == null) {
                // No 'with' section means default behavior (persist-credentials: true)
                if (workflowHasArtifactUpload()) {
                    return SearchResult.found(entry,
                            "Checkout step does not disable credential persistence, which may expose credentials in artifacts.");
                }
            } else {
                // Check persist-credentials setting
                String persistCredentials = getWithValue(withMapping, "persist-credentials");
                if ("true".equals(persistCredentials)) {
                    if (workflowHasArtifactUpload()) {
                        return SearchResult.found(entry,
                                "Checkout step explicitly enables credential persistence, which may expose credentials in artifacts.");
                    }
                }
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkUploadArtifactAction(Yaml.Mapping.Entry entry) {
            // Look for 'with' section to check the path
            Yaml.Mapping stepMapping = findParentStepMapping();
            if (stepMapping == null) {
                return entry;
            }

            Yaml.Mapping withMapping = findWithMapping(stepMapping);
            if (withMapping == null) {
                return entry;
            }

            String pathValue = getWithValue(withMapping, "path");
            if (pathValue != null && hasDangerousArtifactPaths(pathValue)) {
                return SearchResult.found(entry,
                        "Uploading potentially sensitive paths that may contain credentials or configuration files.");
            }

            return entry;
        }

        private void checkWorkflowForCredentialPersistence(Yaml.Mapping workflowMapping) {
            // Implement workflow-level validation using document visitor pattern
            new YamlIsoVisitor<Void>() {
                @Override
                public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, Void ctx) {
                    if ("uses".equals(entry.getKey().getValue()) && entry.getValue() instanceof Yaml.Scalar) {
                        String usesValue = ((Yaml.Scalar) entry.getValue()).getValue();
                        if (usesValue != null && (usesValue.startsWith("actions/checkout") || usesValue.startsWith("actions/upload-artifact"))) {
                            // Additional workflow-level analysis can be added here
                        }
                    }
                    return super.visitMappingEntry(entry, ctx);
                }
            }.visit(workflowMapping, null);
        }

        private boolean workflowHasArtifactUpload() {
            // Walk up to find the document and check if it has upload-artifact actions
            Cursor current = getCursor();
            while (current != null) {
                Object value = current.getValue();
                if (value instanceof Yaml.Document) {
                    Yaml.Document doc = (Yaml.Document) value;
                    return containsUploadArtifact(doc);
                }
                current = current.getParent();
            }
            return false;
        }

        private boolean containsUploadArtifact(Yaml.Document document) {
            AtomicBoolean found = new AtomicBoolean(false);
            new YamlIsoVisitor<Void>() {
                @Override
                public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, Void ctx) {
                    if ("uses".equals(entry.getKey().getValue()) &&
                        entry.getValue() instanceof Yaml.Scalar) {
                        String usesValue = ((Yaml.Scalar) entry.getValue()).getValue();
                        if (usesValue != null && usesValue.startsWith("actions/upload-artifact")) {
                            found.set(true);
                        }
                    }
                    return super.visitMappingEntry(entry, ctx);
                }
            }.visit(document, null);
            return found.get();
        }

        private boolean hasDangerousArtifactPaths(String pathValue) {
            // Check for exact matches
            for (String dangerousPath : DANGEROUS_PATHS) {
                if (pathValue.contains(dangerousPath)) {
                    return true;
                }
            }

            // Check for pattern matches
            String lowerPath = pathValue.toLowerCase();
            for (String pattern : DANGEROUS_PATTERNS) {
                if (lowerPath.contains(pattern)) {
                    return true;
                }
            }

            // Check for current directory or home directory uploads
            if (".".equals(pathValue.trim()) || "~".equals(pathValue.trim()) || "/".equals(pathValue.trim())) {
                return true;
            }

            return false;
        }

        private Yaml.Mapping findParentStepMapping() {
            // Walk up cursor to find the step mapping that contains this 'uses' entry
            Cursor current = getCursor();
            while (current != null) {
                Object value = current.getValue();
                if (value instanceof Yaml.Mapping) {
                    Yaml.Mapping mapping = (Yaml.Mapping) value;
                    // Check if this mapping has 'uses'
                    boolean hasUses = mapping.getEntries().stream()
                            .anyMatch(mapEntry -> "uses".equals(mapEntry.getKey().getValue()));

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
                if ("with".equals(stepEntry.getKey().getValue()) && stepEntry.getValue() instanceof Yaml.Mapping) {
                    return (Yaml.Mapping) stepEntry.getValue();
                }
            }
            return null;
        }

        private String findNestedScalarValue(Yaml.Mapping mapping, String parentKey, String childKey) {
            return mapping.getEntries().stream()
                .filter(entry -> parentKey.equals(entry.getKey().getValue()))
                .filter(entry -> entry.getValue() instanceof Yaml.Mapping)
                .map(entry -> (Yaml.Mapping) entry.getValue())
                .flatMap(childMapping -> childMapping.getEntries().stream())
                .filter(childEntry -> childKey.equals(childEntry.getKey().getValue()))
                .filter(childEntry -> childEntry.getValue() instanceof Yaml.Scalar)
                .map(childEntry -> ((Yaml.Scalar) childEntry.getValue()).getValue())
                .findFirst()
                .orElse(null);
        }

        private String getWithValue(Yaml.Mapping withMapping, String key) {
            for (Yaml.Mapping.Entry withEntry : withMapping.getEntries()) {
                if (key.equals(withEntry.getKey().getValue())) {
                    return withEntry.getValue() instanceof Yaml.Scalar ? ((Yaml.Scalar) withEntry.getValue()).getValue() : null;
                }
            }
            return null;
        }
    }
}
