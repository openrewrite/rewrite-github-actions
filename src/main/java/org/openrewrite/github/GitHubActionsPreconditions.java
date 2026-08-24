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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;

public class GitHubActionsPreconditions {

    private GitHubActionsPreconditions() {
    }

    /**
     * Steps, and the {@code uses:} references within them, appear both in workflow files under
     * {@code $.jobs.*.steps} and in the {@code $.runs.steps} of composite action definitions. Use this
     * in favor of {@link IsGitHubActionsWorkflow} alone for any recipe that operates on steps, so that
     * composite actions are covered too. Recipes that read workflow-only keys such as {@code on:},
     * {@code permissions:}, {@code runs-on:} or {@code needs:} should keep the narrower precondition.
     */
    public static TreeVisitor<?, ExecutionContext> workflowOrActionDefinition() {
        return Preconditions.or(
                new IsGitHubActionsWorkflow().getVisitor(),
                new IsGitHubActionDefinition().getVisitor());
    }
}
