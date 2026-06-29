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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;

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

    @Option(displayName = "Old commit SHA",
            description = "Restricts the change by the existing `uses:` ref. When omitted, the " +
                    "version is changed regardless of how the action is pinned (the default; commit " +
                    "SHA pins are rewritten). When set to an empty string, only references that are " +
                    "**not** pinned to a 40-character commit SHA are changed, preserving deliberate " +
                    "SHA pins. When set to a specific commit SHA, only references pinned to exactly " +
                    "that SHA are changed.",
            required = false,
            example = "8f4b7f84864484a7bf31766abe9204da3cbe65b3")
    @Nullable
    String oldSha;

    String displayName = "Change GitHub Action version";

    String description = "Change the version of a GitHub Action in any workflow.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new ChangeUsesVisitor(
                        "$.jobs..[?(@.uses =~ '" + action + "(?:@.+)?')].uses",
                        oldSha,
                        current -> current.split("@", 2)[0] + '@' + version));
    }
}
