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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class SetupNodeUpgradeNodeVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SetupNodeUpgradeNodeVersion(null));
    }

    @DocumentExample
    @Test
    void upgradeNodeVersion() {
        rewriteRun(
                yaml(
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: '18'
                              - run: npm ci
                              - run: npm test
                        """,
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: '24'
                              - run: npm ci
                              - run: npm test
                        """,
                        spec -> spec.path(".github/workflows/ci.yml")
                )
        );
    }

    @Test
    void upgradeNodeVersionFromNode20() {
        rewriteRun(
                yaml(
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: '20'
                              - run: npm ci
                              - run: npm test
                        """,
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: '24'
                              - run: npm ci
                              - run: npm test
                        """,
                        spec -> spec.path(".github/workflows/ci.yml")
                )
        );
    }

    @Test
    void upgradeNodeVersionFromVersionWithPatch() {
        rewriteRun(
                yaml(
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v3
                                with:
                                  node-version: '18.17.1'
                              - run: npm ci
                              - run: npm test
                        """,
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v3
                                with:
                                  node-version: '24'
                              - run: npm ci
                              - run: npm test
                        """,
                        spec -> spec.path(".github/workflows/ci.yml")
                )
        );
    }

    @Test
    void doNotUpgradeAlreadyCurrentVersion() {
        rewriteRun(
                yaml(
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: '24'
                              - run: npm ci
                              - run: npm test
                        """,
                        spec -> spec.path(".github/workflows/ci.yml")
                )
        );
    }

    @Test
    void doNotUpgradeNewerVersion() {
        rewriteRun(
                yaml(
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: '25'
                              - run: npm ci
                              - run: npm test
                        """,
                        spec -> spec.path(".github/workflows/ci.yml")
                )
        );
    }

    @Test
    void ignoreNonVersionValues() {
        rewriteRun(
                yaml(
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: 'lts/*'
                              - run: npm ci
                              - run: npm test
                        """,
                        spec -> spec.path(".github/workflows/ci.yml")
                )
        );
    }

    @Test
    void ignoreLatestKeyword() {
        rewriteRun(
                yaml(
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: 'latest'
                              - run: npm ci
                              - run: npm test
                        """,
                        spec -> spec.path(".github/workflows/ci.yml")
                )
        );
    }

    @Test
    void customMinimumVersion() {
        rewriteRun(
                spec -> spec.recipe(new SetupNodeUpgradeNodeVersion(20)),
                yaml(
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: '18'
                              - run: npm ci
                              - run: npm test
                        """,
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: '20'
                              - run: npm ci
                              - run: npm test
                        """,
                        spec -> spec.path(".github/workflows/ci.yml")
                )
        );
    }

    @Test
    void multipleJobsUpgrade() {
        rewriteRun(
                yaml(
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: '16'
                              - run: npm test
                          build:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: '18'
                              - run: npm run build
                        """,
                        """
                        name: CI
                        on:
                          pull_request:
                        jobs:
                          test:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: '24'
                              - run: npm test
                          build:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v4
                              - uses: actions/setup-node@v4
                                with:
                                  node-version: '24'
                              - run: npm run build
                        """,
                        spec -> spec.path(".github/workflows/ci.yml")
                )
        );
    }
}