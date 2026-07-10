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
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

public class IsGitHubActionDefinition extends Recipe {

    @Getter
    final String displayName = "Is GitHub Action definition";

    @Getter
    final String description = "Checks if the file is a GitHub Action definition (`action.yml`), " +
            "such as a composite action.";

    /**
     * Matches (composite) action definitions by the required {@code action.yml}/{@code action.yaml}
     * filename rather than by directory. The conventional {@code .github/actions/} location is just
     * that, a convention, so keying on the filename also covers local actions kept elsewhere
     * and actions defined at the repository root.
     */
    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new FindSourceFiles("**/action.{yml,yaml}").getVisitor();
    }
}
