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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class SecretsInheritRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SecretsInheritRecipe());
    }

    @Test
    @DocumentExample
    void shouldDetectSecretsInherit() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  call-workflow:
                    uses: ./.github/workflows/reusable.yml
                    secrets: inherit
                    with:
                      environment: production
                """,
                """
                on: push
                jobs:
                  call-workflow:
                    uses: ./.github/workflows/reusable.yml
                    ~~(This reusable workflow unconditionally inherits all parent secrets. Consider explicitly passing only the required secrets to follow the principle of least privilege and reduce the risk of secret exposure to called workflows.)~~>secrets: inherit
                    with:
                      environment: production
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectSecretsInheritInComplexWorkflow() {
        rewriteRun(
            yaml(
                """
                name: Complex Workflow
                on:
                  push:
                    branches: [main]
                  workflow_dispatch:
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - run: npm ci
                      - run: npm test

                  deploy:
                    needs: build
                    uses: ./.github/workflows/deploy.yml
                    secrets: inherit
                    with:
                      environment: ${{ github.ref == 'refs/heads/main' && 'production' || 'staging' }}

                  notify:
                    needs: deploy
                    runs-on: ubuntu-latest
                    steps:
                      - name: Send notification
                        run: echo "Deployment complete"
                """,
                """
                name: Complex Workflow
                on:
                  push:
                    branches: [main]
                  workflow_dispatch:
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - run: npm ci
                      - run: npm test

                  deploy:
                    needs: build
                    uses: ./.github/workflows/deploy.yml
                    ~~(This reusable workflow unconditionally inherits all parent secrets. Consider explicitly passing only the required secrets to follow the principle of least privilege and reduce the risk of secret exposure to called workflows.)~~>secrets: inherit
                    with:
                      environment: ${{ github.ref == 'refs/heads/main' && 'production' || 'staging' }}

                  notify:
                    needs: deploy
                    runs-on: ubuntu-latest
                    steps:
                      - name: Send notification
                        run: echo "Deployment complete"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectMultipleSecretsInherit() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  deploy-staging:
                    uses: ./.github/workflows/deploy.yml
                    secrets: inherit
                    with:
                      environment: staging

                  deploy-production:
                    needs: deploy-staging
                    uses: ./.github/workflows/deploy.yml
                    secrets: inherit
                    with:
                      environment: production
                """,
                """
                on: push
                jobs:
                  deploy-staging:
                    uses: ./.github/workflows/deploy.yml
                    ~~(This reusable workflow unconditionally inherits all parent secrets. Consider explicitly passing only the required secrets to follow the principle of least privilege and reduce the risk of secret exposure to called workflows.)~~>secrets: inherit
                    with:
                      environment: staging

                  deploy-production:
                    needs: deploy-staging
                    uses: ./.github/workflows/deploy.yml
                    ~~(This reusable workflow unconditionally inherits all parent secrets. Consider explicitly passing only the required secrets to follow the principle of least privilege and reduce the risk of secret exposure to called workflows.)~~>secrets: inherit
                    with:
                      environment: production
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagExplicitSecrets() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  call-workflow:
                    uses: ./.github/workflows/reusable.yml
                    secrets:
                      API_TOKEN: ${{ secrets.API_TOKEN }}
                      DATABASE_URL: ${{ secrets.DATABASE_URL }}
                    with:
                      environment: production
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagSecretsInRegularJobs() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - name: Run tests
                        run: npm test
                        env:
                          API_TOKEN: ${{ secrets.API_TOKEN }}
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagWorkflowsWithoutSecrets() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  call-workflow:
                    uses: ./.github/workflows/reusable.yml
                    with:
                      environment: production
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagNonInheritSecrets() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  call-workflow:
                    uses: ./.github/workflows/reusable.yml
                    secrets:
                      specific-secret: ${{ secrets.SPECIFIC_SECRET }}
                    with:
                      environment: production
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldHandleExternalReusableWorkflows() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  call-external-workflow:
                    uses: org/repo/.github/workflows/reusable.yml@v1
                    secrets: inherit
                    with:
                      config: value
                """,
                """
                on: push
                jobs:
                  call-external-workflow:
                    uses: org/repo/.github/workflows/reusable.yml@v1
                    ~~(This reusable workflow unconditionally inherits all parent secrets. Consider explicitly passing only the required secrets to follow the principle of least privilege and reduce the risk of secret exposure to called workflows.)~~>secrets: inherit
                    with:
                      config: value
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldHandleConditionalReusableWorkflows() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  conditional-deploy:
                    if: github.ref == 'refs/heads/main'
                    uses: ./.github/workflows/deploy.yml
                    secrets: inherit
                    with:
                      environment: production
                """,
                """
                on: push
                jobs:
                  conditional-deploy:
                    if: github.ref == 'refs/heads/main'
                    uses: ./.github/workflows/deploy.yml
                    ~~(This reusable workflow unconditionally inherits all parent secrets. Consider explicitly passing only the required secrets to follow the principle of least privilege and reduce the risk of secret exposure to called workflows.)~~>secrets: inherit
                    with:
                      environment: production
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }
}