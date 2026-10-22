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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class SetupGoUpgradeGoVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SetupGoUpgradeGoVersion("1.26"));
    }

    @DocumentExample
    @Test
    void upgradeGoVersion() {
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
                    - uses: actions/checkout@v5
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '1.22'
                    - run: go test ./...
              """,
            """
              name: CI
              on:
                pull_request:
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v5
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '1.26'
                    - run: go test ./...
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void upgradePatchAndPrereleaseVersionsPreservingScalarStyle() {
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
                    - uses: actions/setup-go@v5
                      with:
                        go-version: "1.21.5"
                    - uses: actions/setup-go@v5
                      with:
                        go-version: 1.20
                    - uses: actions/setup-go@v5
                      with:
                        go-version: '1.25.0-rc.1'
              """,
            """
              name: CI
              on:
                pull_request:
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-go@v5
                      with:
                        go-version: "1.26"
                    - uses: actions/setup-go@v5
                      with:
                        go-version: 1.26
                    - uses: actions/setup-go@v5
                      with:
                        go-version: '1.26'
              """,
            spec -> spec.path(".github/workflows/ci.yml")
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
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '1.22.x'
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '~1.23.0'
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '>=1.21 <1.26'
              """,
            """
              name: CI
              on:
                pull_request:
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '1.26'
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '1.26'
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '1.26'
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void preserveCurrentNewerOpenEndedAndAliasVersions() {
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
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '1.26'
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '1.27.1'
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '^1.21'
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '>=1.21'
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '1.x'
                    - uses: actions/setup-go@v6
                      with:
                        go-version: stable
                    - uses: actions/setup-go@v6
                      with:
                        go-version: oldstable
                    - uses: actions/setup-go@v6
                      with:
                        go-version: ${{ inputs.go-version }}
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void skipGoVersionFile() {
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
                    - uses: actions/setup-go@v6
                      with:
                        go-version-file: go.mod
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '1.22'
                        go-version-file: go.mod
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void addTargetVersionToReferencedMatrixAxis() {
        rewriteRun(
          yaml(
            """
              name: CI
              on:
                pull_request:
              jobs:
                test:
                  runs-on: ${{ matrix.os }}
                  strategy:
                    matrix:
                      go: ['1.23', '1.24']
                      os: [ubuntu-latest, windows-latest]
                  steps:
                    - uses: actions/setup-go@v6
                      with:
                        go-version: ${{ matrix.go }}
                current:
                  runs-on: ubuntu-latest
                  strategy:
                    matrix:
                      go: ['1.25', '1.26']
                  steps:
                    - uses: actions/setup-go@v6
                      with:
                        go-version: ${{ matrix.go }}
                aliased:
                  runs-on: ubuntu-latest
                  strategy:
                    matrix:
                      go: [oldstable, stable]
                  steps:
                    - uses: actions/setup-go@v6
                      with:
                        go-version: ${{ matrix.go }}
              """,
            """
              name: CI
              on:
                pull_request:
              jobs:
                test:
                  runs-on: ${{ matrix.os }}
                  strategy:
                    matrix:
                      go: ['1.23', '1.24', '1.26']
                      os: [ubuntu-latest, windows-latest]
                  steps:
                    - uses: actions/setup-go@v6
                      with:
                        go-version: ${{ matrix.go }}
                current:
                  runs-on: ubuntu-latest
                  strategy:
                    matrix:
                      go: ['1.25', '1.26']
                  steps:
                    - uses: actions/setup-go@v6
                      with:
                        go-version: ${{ matrix.go }}
                aliased:
                  runs-on: ubuntu-latest
                  strategy:
                    matrix:
                      go: [oldstable, stable]
                  steps:
                    - uses: actions/setup-go@v6
                      with:
                        go-version: ${{ matrix.go }}
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
                    - uses: example/setup-go@v6
                      with:
                        go-version: '1.22'
                    - uses: actions/setup-python@v6
                      with:
                        python-version: '1.22'
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          ),
          yaml(
            """
              jobs:
                test:
                  steps:
                    - uses: actions/setup-go@v6
                      with:
                        go-version: '1.22'
              """,
            spec -> spec.path("config.yml")
          )
        );
    }
}
