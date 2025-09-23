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

class UnpinnedDockerImagesRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnpinnedDockerImagesRecipe());
    }

    @DocumentExample
    @Test
    void shouldFlagUnpinnedContainerImage() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      image: node:18
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      ~~(Docker image 'node:18' is not pinned to a digest. Consider pinning to a specific digest for security and reproducibility.)~~>image: node:18
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagUnpinnedServiceImage() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    services:
                      postgres:
                        image: postgres:15
                        env:
                          POSTGRES_PASSWORD: postgres
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    services:
                      postgres:
                        ~~(Docker image 'postgres:15' is not pinned to a digest. Consider pinning to a specific digest for security and reproducibility.)~~>image: postgres:15
                        env:
                          POSTGRES_PASSWORD: postgres
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagDockerProtocolImage() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Run in Docker
                        image: docker://ubuntu:22.04
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Run in Docker
                        ~~(Docker image 'docker://ubuntu:22.04' is not pinned to a digest. Consider pinning to a specific digest for security and reproducibility.)~~>image: docker://ubuntu:22.04
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagDigestPinnedImage() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      image: node:18@sha256:a6b8c8f0e01d86cc1a4d6f1c1c1d1a1d1a1d1a1d1a1d1a1d1a1d1a1d1a1d1a1d
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagImageWithInvalidDigest() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      image: node:18@sha256:invalid
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      ~~(Docker image 'node:18@sha256:invalid' is not pinned to a digest. Consider pinning to a specific digest for security and reproducibility.)~~>image: node:18@sha256:invalid
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagNonImageFields() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Not an image
                        run: echo "image node:18"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagMultipleUnpinnedImages() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      image: node:18
                    services:
                      postgres:
                        image: postgres:15
                      redis:
                        image: redis:7@sha256:a6b8c8f0e01d86cc1a4d6f1c1c1d1a1d1a1d1a1d1a1d1a1d1a1d1a1d1a1d1a1d
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    container:
                      ~~(Docker image 'node:18' is not pinned to a digest. Consider pinning to a specific digest for security and reproducibility.)~~>image: node:18
                    services:
                      postgres:
                        ~~(Docker image 'postgres:15' is not pinned to a digest. Consider pinning to a specific digest for security and reproducibility.)~~>image: postgres:15
                      redis:
                        image: redis:7@sha256:a6b8c8f0e01d86cc1a4d6f1c1c1d1a1d1a1d1a1d1a1d1a1d1a1d1a1d1a1d1a1d
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }
}
