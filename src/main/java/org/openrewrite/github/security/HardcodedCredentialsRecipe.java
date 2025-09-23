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

import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class HardcodedCredentialsRecipe extends Recipe {

    private static final Pattern GITHUB_EXPRESSION_PATTERN = Pattern.compile("\\$\\{\\{.*?\\}\\}");

    @Override
    public String getDisplayName() {
        return "Find hardcoded container credentials";
    }

    @Override
    public String getDescription() {
        return "Detects hardcoded credentials in GitHub Actions container configurations. " +
                "Container registry passwords should use secrets instead of hardcoded values. " +
                "Based on [zizmor's hardcoded-container-credentials audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/hardcoded_container_credentials.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml"),
                new HardcodedCredentialsVisitor()
        );
    }

    private static class HardcodedCredentialsVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            // Look for password entries - simple pattern matching
            if (isPasswordEntry(mappingEntry)) {
                String passwordValue = getPasswordValue(mappingEntry);
                if (passwordValue != null && isHardcodedPassword(passwordValue)) {
                    return SearchResult.found(mappingEntry,
                            "Container registry password '" + passwordValue + "' appears to be hardcoded. " +
                                    "Use secrets (e.g., ${{ secrets.REGISTRY_PASSWORD }}) instead.");
                }
            }

            return mappingEntry;
        }

        private boolean isPasswordEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getKey() instanceof Yaml.Scalar)) {
                return false;
            }

            String key = ((Yaml.Scalar) entry.getKey()).getValue();
            return "password".equals(key);
        }

        private String getPasswordValue(Yaml.Mapping.Entry entry) {
            if (entry.getValue() instanceof Yaml.Scalar) {
                return ((Yaml.Scalar) entry.getValue()).getValue();
            }
            return null;
        }

        private boolean isHardcodedPassword(String passwordValue) {
            // If the password doesn't contain GitHub expression syntax, it's hardcoded
            return !GITHUB_EXPRESSION_PATTERN.matcher(passwordValue).find();
        }
    }
}
