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
package org.openrewrite.github;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.yaml.ChangeValue;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.search.FindKey;
import org.openrewrite.yaml.tree.Yaml;

@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeActionVersion extends Recipe {
    @Option(displayName = "Action",
            description = "Name of the action to update.",
            example = "actions/setup-java")
    String action;

    @Option(displayName = "Version",
            description = "Version to use.",
            example = "v4")
    String version;

    String displayName = "Change GitHub Action version";

    String description = "Change the version of a GitHub Action in any workflow.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                        Yaml.Documents docs = super.visitDocuments(documents, ctx);
                        // Find all 'uses' entries that match the specified action
                        for (Yaml uses : FindKey.find(docs, "$.jobs..[?(@.uses =~ '" + action + "(?:@.+)?')].uses")) {
                            if (!(uses instanceof Yaml.Mapping.Entry)) {
                                continue;
                            }
                            if (!(((Yaml.Mapping.Entry) uses).getValue() instanceof Yaml.Scalar)) {
                                continue;
                            }

                            // Extract the old action name (without version), and replace with the new version
                            String oldAction = ((Yaml.Scalar) ((Yaml.Mapping.Entry) uses).getValue()).getValue().split("@")[0];
                            docs = (Yaml.Documents) new ChangeValue(
                                    "$.jobs..[?(@.uses =~ '" + oldAction + "(?:@.+)?')].uses",
                                    oldAction + '@' + version, null)
                                    .getVisitor()
                                    .visitNonNull(docs, ctx);
                        }
                        return docs;
                    }
                }
        );
    }
}
