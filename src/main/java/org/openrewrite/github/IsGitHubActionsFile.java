/*
 * Copyright 2026 the original author or authors.
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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

public class IsGitHubActionsFile extends Recipe {

    @Getter
    final String displayName = "Is GitHub Actions workflow or action definition";

    @Getter
    final String description = "Checks if the file is either a GitHub Actions workflow file, or a GitHub Action " +
            "definition (`action.yml`). Steps, and the `uses:` references within them, appear in both, so prefer " +
            "this over `IsGitHubActionsWorkflow` as a precondition for any recipe that operates on steps. Recipes " +
            "that read workflow-only keys such as `on:`, `permissions:`, `runs-on:` or `needs:` should keep the " +
            "narrower `IsGitHubActionsWorkflow`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.or(
                new IsGitHubActionsWorkflow().getVisitor(),
                new IsGitHubActionDefinition().getVisitor());
    }
}
