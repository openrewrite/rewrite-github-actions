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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.yaml.Assertions.yaml;

class IsGitHubActionsFileTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new IsGitHubActionsFile());
    }

    @DocumentExample
    @Test
    void detectCompositeAction() {
        rewriteRun(
          //language=yml
          yaml(
            """
              runs:
                using: composite
                steps:
                  - uses: actions/checkout@v4
              """,
            """
              runs:
                using: composite
                steps:
                  - uses: actions/checkout@v4
              """,
            spec -> spec.path(".github/actions/build/action.yml")
              .afterRecipe(docs -> assertThat(docs.getMarkers().findFirst(SearchResult.class)).isPresent())
          )
        );
    }

    @Test
    void detectWorkflow() {
        rewriteRun(
          //language=yml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
              """,
            """
              on:
                push:
                  branches:
                    - main
              """,
            spec -> spec.path(".github/workflows/ci.yaml")
              .afterRecipe(docs -> assertThat(docs.getMarkers().findFirst(SearchResult.class)).isPresent())
          )
        );
    }

    @Test
    void notFoundForOtherYamlFiles() {
        rewriteRun(
          //language=yml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
              """,
            spec -> spec.path(".github/workflow/ci.yml")
              .afterRecipe(docs -> assertThat(docs.getMarkers().findFirst(SearchResult.class)).isEmpty())
          )
        );
    }
}
