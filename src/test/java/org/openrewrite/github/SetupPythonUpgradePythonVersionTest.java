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

class SetupPythonUpgradePythonVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SetupPythonUpgradePythonVersion("3.14"));
    }

    @DocumentExample
    @Test
    void upgradePythonVersion() {
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
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.10'
                    - run: python -m pytest
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
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.14'
                    - run: python -m pytest
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void customTargetVersion() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonUpgradePythonVersion("3.12")),
          yaml(
            """
              name: CI
              on:
                pull_request:
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.10.1'
              """,
            """
              name: CI
              on:
                pull_request:
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.12'
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void preserveScalarStyle() {
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
                    - uses: actions/setup-python@v5
                      with:
                        python-version: "3.10"
                    - uses: actions/setup-python@v5
                      with:
                        python-version: 3.10
              """,
            """
              name: CI
              on:
                pull_request:
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-python@v5
                      with:
                        python-version: "3.14"
                    - uses: actions/setup-python@v5
                      with:
                        python-version: 3.14
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void skipPythonVersionFile() {
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
                    - uses: actions/setup-python@v5
                      with:
                        python-version-file: .python-version
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void skipWhenPythonVersionFileAndPythonVersionAreBothPresent() {
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
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.10'
                        python-version-file: .python-version
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void preserveAlreadyCurrentAndNewerVersions() {
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
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.14'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.15'
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void upgradeMultipleJobsAndWorkflows() {
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
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.9'
                lint:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-python@v4
                      with:
                        python-version: '3.11'
              """,
            """
              name: CI
              on:
                pull_request:
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.14'
                lint:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-python@v4
                      with:
                        python-version: '3.14'
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          ),
          yaml(
            """
              name: Release
              on:
                workflow_dispatch:
              jobs:
                release:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.8'
              """,
            """
              name: Release
              on:
                workflow_dispatch:
              jobs:
                release:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.14'
              """,
            spec -> spec.path(".github/workflows/release.yaml")
          )
        );
    }

    @Test
    void upgradeSafelyOlderRanges() {
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
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '>=3.10 <3.14'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '<=3.13'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.13.x'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '~3.13.0'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '~3.12'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '2.x'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '2.*'
              """,
            """
              name: CI
              on:
                pull_request:
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.14'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.14'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.14'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.14'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.14'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.14'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.14'
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void preserveNonOlderRangesAndDynamicValues() {
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
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '>=3.10 <3.15'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '>=4 || <3.14'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '>3.14 <3.15'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '>=3.15 <4'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.15.x'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '~3.15'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: ${{ matrix.python-version }}
                    - uses: actions/setup-python@v5
                      with:
                        python-version: pypy3.10
                    - uses: actions/setup-python@v5
                      with:
                        python-version: graalpy-24.0
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void upgradeLoneBlockScalarVersionButNotAMatrix() {
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
                    - uses: actions/setup-python@v5
                      with:
                        python-version: |
                          3.10
                    - uses: actions/setup-python@v5
                      with:
                        python-version: >
                          3.11
                    - uses: actions/setup-python@v5
                      with:
                        python-version: |
                          3.10
                          3.11
              """,
            """
              name: CI
              on:
                pull_request:
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-python@v5
                      with:
                        python-version: |
                          3.14
                    - uses: actions/setup-python@v5
                      with:
                        python-version: >
                          3.14
                    - uses: actions/setup-python@v5
                      with:
                        python-version: |
                          3.10
                          3.11
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void ignoreOtherActionsAndNonWorkflowFiles() {
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
                    - uses: example/setup-python@v5
                      with:
                        python-version: '3.10'
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          ),
          yaml(
            """
              jobs:
                test:
                  steps:
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.10'
              """,
            spec -> spec.path("config.yml")
          )
        );
    }

    @Test
    void ignoreWorkflowWithoutSetupPython() {
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
                    - run: python -m pytest
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
