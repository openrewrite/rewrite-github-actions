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

@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeAction extends Recipe {
    @Option(displayName = "Action",
            description = "Name of the action to match.",
            example = "gradle/wrapper-validation-action")
    String oldAction;

    @Option(displayName = "Action",
            description = "Name of the action to use instead.",
            example = "gradle/actions/wrapper-validation")
    String newAction;

    @Option(displayName = "Version",
            description = "New version to use.",
            example = "v3")
    String newVersion;

    @Override
    public String getDisplayName() {
        return "Change GitHub Action";
    }

    @Override
    public String getDescription() {
        return "Change a GitHub Action in any `.github/workflows/*.{yml,yaml}` file.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new ChangeValue(
                        "$.jobs..[?(@.uses =~ '" + oldAction + "(?:@.+)?')].uses",
                        newAction + '@' + newVersion, null).getVisitor());
    }
}
