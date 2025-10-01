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

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class ExcessivePermissionsRecipe extends Recipe {

    private static final Set<String> HIGH_RISK_PERMISSIONS = new HashSet<>(Arrays.asList(
            "actions", "attestations", "contents", "deployments", "id-token",
            "issues", "packages", "pages", "pull-requests"
    ));

    private static final Set<String> MEDIUM_RISK_PERMISSIONS = new HashSet<>(Arrays.asList(
            "checks", "discussions", "repository-projects", "security-events"
    ));

    @Override
    public String getDisplayName() {
        return "Find excessive permissions";
    }

    @Override
    public String getDescription() {
        return "Find overly broad permissions in GitHub Actions workflows. " +
                "Flags 'write-all' permissions and excessive write permissions that " +
                "could be scoped more narrowly for security. " +
                "Based on [zizmor's excessive-permissions audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/excessive_permissions.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml"),
                new ExcessivePermissionsVisitor()
        );
    }

    private static class ExcessivePermissionsVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            if (isPermissionsEntry(mappingEntry)) {
                return checkPermissions(mappingEntry);
            }

            return mappingEntry;
        }

        private boolean isPermissionsEntry(Yaml.Mapping.Entry entry) {
            return "permissions".equals(entry.getKey().getValue());
        }

        private Yaml.Mapping.Entry checkPermissions(Yaml.Mapping.Entry entry) {
            String scalarPermissionValue = entry.getValue() instanceof Yaml.Scalar ? ((Yaml.Scalar) entry.getValue()).getValue() : null;
            if (scalarPermissionValue != null) {
                return checkScalarPermissions(entry, scalarPermissionValue);
            }
            if (entry.getValue() instanceof Yaml.Mapping) {
                return checkMappingPermissions(entry, (Yaml.Mapping) entry.getValue());
            }

            return entry;
        }

        private Yaml.Mapping.Entry checkScalarPermissions(Yaml.Mapping.Entry entry, String permissionValue) {
            switch (permissionValue) {
                case "write-all":
                    return SearchResult.found(entry,
                            "Uses 'write-all' permissions which grants excessive access. " +
                                    "Consider using specific permissions instead.");
                case "read-all":
                    return SearchResult.found(entry,
                            "Uses 'read-all' permissions. Consider using specific permissions " +
                                    "if only certain resources need to be accessed.");
                default:
                    return entry;
            }
        }

        private Yaml.Mapping.Entry checkMappingPermissions(Yaml.Mapping.Entry entry, Yaml.Mapping permissionsMapping) {
            List<String> issues = new ArrayList<>();

            for (Yaml.Mapping.Entry permEntry : permissionsMapping.getEntries()) {
                String permissionName = permEntry.getKey().getValue();
                String permissionValue = permEntry.getValue() instanceof Yaml.Scalar ? ((Yaml.Scalar) permEntry.getValue()).getValue() : null;
                if (permissionName != null && permissionValue != null) {

                    if ("write".equals(permissionValue)) {
                        if (HIGH_RISK_PERMISSIONS.contains(permissionName)) {
                            issues.add(permissionName + ": write (high risk)");
                        } else if (MEDIUM_RISK_PERMISSIONS.contains(permissionName)) {
                            issues.add(permissionName + ": write (medium risk)");
                        }
                    }
                }
            }

            if (!issues.isEmpty()) {
                return SearchResult.found(entry,
                        "Contains potentially excessive write permissions: " + String.join(", ", issues) + ". " +
                                "Consider whether these permissions are necessary and if they can be scoped more narrowly.");
            }

            return entry;
        }
    }
}
