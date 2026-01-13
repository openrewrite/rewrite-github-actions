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
public class UnpinnedActionsRecipe extends Recipe {

    private static final Pattern UNPINNED_ACTION_PATTERN = Pattern.compile(
            "^([^/@]+/[^/@]+)(@(main|master|HEAD|latest|v?\\d+(\\.\\d+)*(\\.\\d+)*))??$"
    );

    private static final Pattern SHA_PATTERN = Pattern.compile("^[a-f0-9]{40}$");

    String displayName = "Pin GitHub Actions to specific commits";

    String description = "Pin GitHub Actions to specific commit SHAs for security and reproducibility. " +
                "Actions pinned to tags or branches can be changed by the action author, " +
                "while SHA pins are immutable. " +
                "Based on [zizmor's unpinned-uses audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/unpinned_uses.rs).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new UnpinnedActionsVisitor()
        );
    }

    private static class UnpinnedActionsVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            if (isUsesEntry(mappingEntry)) {
                String usesValue = getUsesValue(mappingEntry);
                if (usesValue != null && isUnpinned(usesValue)) {
                    return SearchResult.found(mappingEntry,
                            "Action '" + usesValue + "' is not pinned to a commit SHA. " +
                                    "Consider pinning to a specific commit for security and reproducibility.");
                }
            }

            return mappingEntry;
        }

        private boolean isUsesEntry(Yaml.Mapping.Entry entry) {
            // Broader approach - match any "uses" entry and let the logic handle context validation
            return entry.getKey() instanceof Yaml.Scalar &&
                    "uses".equals(((Yaml.Scalar) entry.getKey()).getValue());
        }

        private String getUsesValue(Yaml.Mapping.Entry entry) {
            if (entry.getValue() instanceof Yaml.Scalar) {
                return ((Yaml.Scalar) entry.getValue()).getValue();
            }
            return null;
        }

        private boolean isUnpinned(String usesValue) {
            // Skip local actions (start with ./)
            if (usesValue.startsWith("./")) {
                return false;
            }

            // Skip Docker actions (start with docker://)
            if (usesValue.startsWith("docker://")) {
                return false;
            }

            // Check if it's a repository action
            String[] parts = usesValue.split("@", 2);
            if (parts.length < 2) {
                // No @ symbol means no version specified at all
                return true;
            }

            String version = parts[1];

            // If it's already a SHA, it's pinned
            if (SHA_PATTERN.matcher(version).matches()) {
                return false;
            }

            // If it matches unpinned patterns (main, master, HEAD, latest, or version tags), it's unpinned
            return UNPINNED_ACTION_PATTERN.matcher(usesValue).matches();
        }
    }
}
