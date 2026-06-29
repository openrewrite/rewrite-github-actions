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
public class ChangeAction extends Recipe {
    @Option(displayName = "Action",
            description = "Name of the action to match.",
            example = "gradle/wrapper-validation-action")
    String oldAction;

    @Option(displayName = "Old commit SHA",
            description = "Restricts the change by the existing `uses:` ref. When omitted, the " +
                    "action is changed regardless of how it is pinned (the default; commit SHA pins " +
                    "are rewritten). When set to an empty string, only references that are **not** " +
                    "pinned to a 40-character commit SHA are changed, leaving deliberate SHA pins on " +
                    "the original action untouched. When set to a specific commit SHA, only " +
                    "references pinned to exactly that SHA are changed.",
            required = false,
            example = "8f4b7f84864484a7bf31766abe9204da3cbe65b3")
    @Nullable
    String oldSha;

    @Option(displayName = "Action",
            description = "Name of the action to use instead.",
            example = "gradle/actions/wrapper-validation")
    String newAction;

    @Option(displayName = "Version",
            description = "New version to use.",
            example = "v3")
    String newVersion;

    String displayName = "Change GitHub Action";

    String description = "Change a GitHub Action in any workflow.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new ChangeUsesVisitor(
                        "$.jobs..[?(@.uses =~ '" + oldAction + "(?:@.+)?')].uses",
                        oldSha,
                        current -> newAction + '@' + newVersion));
    }
}
