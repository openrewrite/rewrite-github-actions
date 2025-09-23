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

class ArtifactSecurityRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ArtifactSecurityRecipe());
    }

    @DocumentExample
    @Test
    void shouldFlagCredentialPersistenceRisk() {
        rewriteRun(
            yaml(
                """
                name: Build and Upload
                on: push
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/upload-artifact@v3
                        with:
                          name: build-output
                          path: |
                            ~/.ssh/
                            ~/.gitconfig
                            ~/.aws/
                """,
                """
                name: Build and Upload
                on: push
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Checkout step does not disable credential persistence, which may expose credentials in artifacts.)~~>uses: actions/checkout@v4
                      - ~~(Uploading potentially sensitive paths that may contain credentials or configuration files.)~~>uses: actions/upload-artifact@v3
                        with:
                          name: build-output
                          path: |
                            ~/.ssh/
                            ~/.gitconfig
                            ~/.aws/
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/build.yml")
            )
        );
    }

    @Test
    void shouldNotFlagSecureCheckout() {
        rewriteRun(
            yaml(
                """
                name: Build and Upload
                on: push
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                        with:
                          persist-credentials: false
                      - uses: actions/upload-artifact@v3
                        with:
                          name: build-output
                          path: dist/
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/build.yml")
            )
        );
    }

    @Test
    void shouldFlagDangerousArtifactPaths() {
        rewriteRun(
            yaml(
                """
                name: Upload Secrets
                on: push
                jobs:
                  upload:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/upload-artifact@v3
                        with:
                          name: sensitive-data
                          path: /etc/passwd
                """,
                """
                name: Upload Secrets
                on: push
                jobs:
                  upload:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Uploading potentially sensitive paths that may contain credentials or configuration files.)~~>uses: actions/upload-artifact@v3
                        with:
                          name: sensitive-data
                          path: /etc/passwd
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/build.yml")
            )
        );
    }

    @Test
    void shouldFlagMultipleDangerousPaths() {
        rewriteRun(
            yaml(
                """
                name: Upload Multiple
                on: push
                jobs:
                  upload:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/upload-artifact@v3
                        with:
                          name: logs
                          path: |
                            /var/log/
                            ~/.docker/config.json
                            dist/
                """,
                """
                name: Upload Multiple
                on: push
                jobs:
                  upload:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Uploading potentially sensitive paths that may contain credentials or configuration files.)~~>uses: actions/upload-artifact@v3
                        with:
                          name: logs
                          path: |
                            /var/log/
                            ~/.docker/config.json
                            dist/
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/build.yml")
            )
        );
    }

    @Test
    void shouldNotFlagSafePaths() {
        rewriteRun(
            yaml(
                """
                name: Upload Safe Artifacts
                on: push
                jobs:
                  upload:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/upload-artifact@v3
                        with:
                          name: build-output
                          path: |
                            dist/
                            target/release/
                            build/
                            public/
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/build.yml")
            )
        );
    }

    @Test
    void shouldFlagExplicitCredentialPersistence() {
        rewriteRun(
            yaml(
                """
                name: Explicit Persist
                on: push
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                        with:
                          persist-credentials: true
                      - uses: actions/upload-artifact@v3
                        with:
                          name: source
                          path: .
                """,
                """
                name: Explicit Persist
                on: push
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Checkout step explicitly enables credential persistence, which may expose credentials in artifacts.)~~>uses: actions/checkout@v4
                        with:
                          persist-credentials: true
                      - ~~(Uploading potentially sensitive paths that may contain credentials or configuration files.)~~>uses: actions/upload-artifact@v3
                        with:
                          name: source
                          path: .
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/build.yml")
            )
        );
    }

    @Test
    void shouldNotFlagWithoutArtifactUpload() {
        rewriteRun(
            yaml(
                """
                name: No Upload
                on: push
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - run: echo "Building..."
                      - run: npm test
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/build.yml")
            )
        );
    }

    @Test
    void shouldIgnoreNonWorkflowFiles() {
        rewriteRun(
            yaml(
                """
                version: '3.8'
                services:
                  app:
                    volumes:
                      - ~/.ssh:/root/.ssh
                """,
                sourceSpecs -> sourceSpecs.path("docker-compose.yml")
            )
        );
    }
}
