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
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.yaml.Assertions.yaml;

class MigrateTibdexGitHubAppTokenToActionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.github.MigrateTibdexGitHubAppTokenToActions");
    }

    @DocumentExample
    @Test
    void migratesActionAndRenamesInputs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                token:
                  runs-on: ubuntu-latest
                  steps:
                    - id: create_token
                      uses: tibdex/github-app-token@v2
                      with:
                        app_id: ${{ secrets.APP_ID }}
                        private_key: ${{ secrets.APP_PRIVATE_KEY }}
              """,
            """
              jobs:
                token:
                  runs-on: ubuntu-latest
                  steps:
                    - id: create_token
                      uses: actions/create-github-app-token@v3
                      with:
                        app-id: ${{ secrets.APP_ID }}
                        private-key: ${{ secrets.APP_PRIVATE_KEY }}
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void renamesGitHubApiUrlForEnterpriseServer() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                token:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: tibdex/github-app-token@v2
                      with:
                        app_id: ${{ secrets.APP_ID }}
                        private_key: ${{ secrets.APP_PRIVATE_KEY }}
                        github_api_url: ${{ github.api_url }}
              """,
            """
              jobs:
                token:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/create-github-app-token@v3
                      with:
                        app-id: ${{ secrets.APP_ID }}
                        private-key: ${{ secrets.APP_PRIVATE_KEY }}
                        github-api-url: ${{ github.api_url }}
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void leavesUnrelatedActionsUnchanged() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                      with:
                        token: ${{ secrets.GITHUB_TOKEN }}
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }
}
