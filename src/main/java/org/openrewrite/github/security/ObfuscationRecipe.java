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

import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class ObfuscationRecipe extends Recipe {

    // Pattern to detect potentially obfuscated expressions
    private static final Pattern OBFUSCATED_EXPRESSION_PATTERN = Pattern.compile(
            "\\$\\{\\{[^}]*['\"]}|['\"]{2,}|\\{\\{[^}]*\\$"
    );

    @Override
    public String getDisplayName() {
        return "Find obfuscated GitHub Actions features";
    }

    @Override
    public String getDescription() {
        return "Find workflows that use obfuscated action references or expressions that may be attempting to hide " +
                "malicious behavior. This includes action paths with `'.'`, `'..'`, empty components, or expressions " +
                "that use quote manipulation to hide their true intent. " +
                "Based on [zizmor's `obfuscation` audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/obfuscation.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new ObfuscationVisitor()
        );
    }

    private static class ObfuscationVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            if (isUsesEntry(mappingEntry)) {
                return checkUsesEntry(mappingEntry);
            }

            if (isRunEntry(mappingEntry)) {
                return checkRunEntry(mappingEntry);
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

            // Skip local actions (start with ./) and docker actions
            if (usesValue.startsWith("./") || usesValue.startsWith("docker://")) {
                return entry;
            }

            // Check for obfuscated repository actions
            if (hasObfuscatedPath(usesValue)) {
                return SearchResult.found(entry,
                        "Action reference contains obfuscated path components that may hide the actual action being used.");
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkRunEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getValue() instanceof Yaml.Scalar)) {
                return entry;
            }

            String runCommand = ((Yaml.Scalar) entry.getValue()).getValue();

            // Check for obfuscated expressions
            if (hasObfuscatedExpressions(runCommand)) {
                return SearchResult.found(entry,
                        "Contains potentially obfuscated GitHub Actions expressions that may be attempting to hide malicious code.");
            }

            return entry;
        }

        private boolean hasObfuscatedPath(String usesValue) {
            // Parse the action reference (format: owner/repo[/path]@ref)
            String[] parts = usesValue.split("@", 2);
            if (parts.length < 2) {
                return false; // No version specified
            }

            String actionPath = parts[0];

            // Check for double slashes (which create empty components)
            if (actionPath.contains("//")) {
                return true;
            }

            // Split by '/' and check components
            String[] pathParts = actionPath.split("/");

            // Check all path components for obfuscation patterns
            for (String component : pathParts) {
                // Check for obfuscation patterns
                if (".".equals(component)) {
                    return true; // Current directory reference
                }

                if ("..".equals(component)) {
                    return true; // Parent directory reference
                }
            }

            return false;
        }

        private boolean hasObfuscatedExpressions(String content) {
            // Check for potentially obfuscated GitHub Actions expressions
            return OBFUSCATED_EXPRESSION_PATTERN.matcher(content).find();
        }
    }
}
