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

class HardcodedCredentialsRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new HardcodedCredentialsRecipe());
    }

    @Test
    @DocumentExample
    void shouldDetectHardcodedContainerPassword() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      image: my-registry/image:latest
                      credentials:
                        username: user
                        password: hardcoded-password
                    steps:
                      - run: echo "test"
                """,
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      image: my-registry/image:latest
                      credentials:
                        username: user
                        ~~(Container registry password 'hardcoded-password' appears to be hardcoded. Use secrets (e.g., ${{ secrets.REGISTRY_PASSWORD }}) instead.)~~>password: hardcoded-password
                    steps:
                      - run: echo "test"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectHardcodedServicePassword() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    services:
                      redis:
                        image: redis:latest
                        credentials:
                          username: redis-user
                          password: my-secret-password
                    steps:
                      - run: echo "test"
                """,
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    services:
                      redis:
                        image: redis:latest
                        credentials:
                          username: redis-user
                          ~~(Container registry password 'my-secret-password' appears to be hardcoded. Use secrets (e.g., ${{ secrets.REGISTRY_PASSWORD }}) instead.)~~>password: my-secret-password
                    steps:
                      - run: echo "test"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagValidSecretUsage() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      image: my-registry/image:latest
                      credentials:
                        username: ${{ secrets.REGISTRY_USERNAME }}
                        password: ${{ secrets.REGISTRY_PASSWORD }}
                    steps:
                      - run: echo "test"
                """
            )
        );
    }

    @Test
    void shouldNotFlagEnvironmentVariableUsage() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    services:
                      database:
                        image: postgres:latest
                        credentials:
                          username: ${{ env.DB_USER }}
                          password: ${{ env.DB_PASSWORD }}
                    steps:
                      - run: echo "test"
                """
            )
        );
    }

    @Test
    void shouldNotFlagInputUsage() {
        rewriteRun(
            yaml(
                """
                on:
                  workflow_call:
                    inputs:
                      registry_password:
                        type: string
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      image: my-registry/image:latest
                      credentials:
                        username: user
                        password: ${{ inputs.registry_password }}
                    steps:
                      - run: echo "test"
                """
            )
        );
    }

    @Test
    void shouldNotFlagPasswordsOutsideCredentialsContext() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    env:
                      PASSWORD: hardcoded-but-not-in-credentials
                    steps:
                      - run: echo "test"
                        env:
                          PASSWORD: also-hardcoded-but-not-credentials
                """
            )
        );
    }

    @Test
    void shouldDetectMultipleHardcodedPasswords() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      image: my-registry/image:latest
                      credentials:
                        username: user
                        password: hardcoded1
                    services:
                      redis:
                        image: redis:latest
                        credentials:
                          username: redis-user
                          password: hardcoded2
                      postgres:
                        image: postgres:latest
                        credentials:
                          username: pg-user
                          password: hardcoded3
                    steps:
                      - run: echo "test"
                """,
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      image: my-registry/image:latest
                      credentials:
                        username: user
                        ~~(Container registry password 'hardcoded1' appears to be hardcoded. Use secrets (e.g., ${{ secrets.REGISTRY_PASSWORD }}) instead.)~~>password: hardcoded1
                    services:
                      redis:
                        image: redis:latest
                        credentials:
                          username: redis-user
                          ~~(Container registry password 'hardcoded2' appears to be hardcoded. Use secrets (e.g., ${{ secrets.REGISTRY_PASSWORD }}) instead.)~~>password: hardcoded2
                      postgres:
                        image: postgres:latest
                        credentials:
                          username: pg-user
                          ~~(Container registry password 'hardcoded3' appears to be hardcoded. Use secrets (e.g., ${{ secrets.REGISTRY_PASSWORD }}) instead.)~~>password: hardcoded3
                    steps:
                      - run: echo "test"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }
}