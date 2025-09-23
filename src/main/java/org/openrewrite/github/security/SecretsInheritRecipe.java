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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

@Value
@EqualsAndHashCode(callSuper = false)
public class SecretsInheritRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Find unconditional secrets inheritance";
    }

    @Override
    public String getDescription() {
        return "Detects when reusable workflows unconditionally inherit all parent secrets via `secrets: inherit`. " +
                "This practice can lead to over-privileged workflows and potential secret exposure to called workflows " +
                "that may not need access to all secrets. Consider explicitly passing only required secrets. " +
                "Based on [zizmor's secrets-inherit audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/secrets_inherit.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SecretsInheritVisitor();
    }

    private static class SecretsInheritVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            // Look for "secrets: inherit" - simple pattern matching
            if (isSecretsInheritEntry(mappingEntry)) {
                return SearchResult.found(mappingEntry,
                        "This reusable workflow unconditionally inherits all parent secrets. " +
                                "Consider explicitly passing only the required secrets to follow the principle of least privilege " +
                                "and reduce the risk of secret exposure to called workflows.");
            }

            return mappingEntry;
        }

        private boolean isSecretsInheritEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getKey() instanceof Yaml.Scalar)) {
                return false;
            }

            String key = ((Yaml.Scalar) entry.getKey()).getValue();
            if (!"secrets".equals(key)) {
                return false;
            }

            // Check if the value is "inherit"
            if (!(entry.getValue() instanceof Yaml.Scalar)) {
                return false;
            }

            String value = ((Yaml.Scalar) entry.getValue()).getValue();
            return "inherit".equals(value);
        }
    }
}
