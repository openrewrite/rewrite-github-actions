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

class ReplaceAlwaysWithSuccessOrFailureTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceAlwaysWithSuccessOrFailure());
    }

    @DocumentExample
    @Test
    void replacesJobAndStepConditions() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                build:
                  if: always()
                  runs-on: ubuntu-latest
                  steps:
                    - name: Upload results
                      if: ${{ always() }}
                      run: ./upload-results.sh
              """,
            """
              on: push
              jobs:
                build:
                  if: success() || failure()
                  runs-on: ubuntu-latest
                  steps:
                    - name: Upload results
                      if: ${{ success() || failure() }}
                      run: ./upload-results.sh
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void replacesCompositeActionStepCondition() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              name: Upload results
              description: Upload test results
              runs:
                using: composite
                steps:
                  - if: always()
                    shell: bash
                    run: ./upload-results.sh
              """,
            """
              name: Upload results
              description: Upload test results
              runs:
                using: composite
                steps:
                  - if: success() || failure()
                    shell: bash
                    run: ./upload-results.sh
              """,
            spec -> spec.path(".github/actions/upload/action.yml")
          )
        );
    }

    @Test
    void preservesPrecedenceInLargerExpressions() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - if: always() && github.ref == 'refs/heads/main'
                      run: ./publish.sh
                    - if: ${{ cancelled() || always() }}
                      run: ./cleanup.sh
              """,
            """
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - if: (success() || failure()) && github.ref == 'refs/heads/main'
                      run: ./publish.sh
                    - if: ${{ cancelled() || (success() || failure()) }}
                      run: ./cleanup.sh
              """,
            spec -> spec.path(".github/workflows/ci.yaml")
          )
        );
    }

    @Test
    void replacesCallsWithInternalWhitespace() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                build:
                  if: ${{ always ( ) }}
                  runs-on: ubuntu-latest
                  steps:
                    - run: ./build.sh
              """,
            """
              on: push
              jobs:
                build:
                  if: ${{ success() || failure() }}
                  runs-on: ubuntu-latest
                  steps:
                    - run: ./build.sh
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void preservesBlockScalarStyle() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - if: >-
                        always() &&
                        github.ref == 'refs/heads/main'
                      run: ./publish.sh
              """,
            """
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - if: >-
                        (success() || failure()) &&
                        github.ref == 'refs/heads/main'
                      run: ./publish.sh
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotChangeOtherConditionsOrValues() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                build:
                  if: notalways()
                  runs-on: ubuntu-latest
                  env:
                    DESCRIPTION: always()
                  steps:
                    - if: success() || failure()
                      run: echo always()
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotReplaceTextInsideStringLiterals() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - if: contains(github.event.head_commit.message, 'always()')
                      run: ./build.sh
                    - if: always() && contains(github.event.head_commit.message, 'always()')
                      run: ./publish.sh
              """,
            """
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - if: contains(github.event.head_commit.message, 'always()')
                      run: ./build.sh
                    - if: (success() || failure()) && contains(github.event.head_commit.message, 'always()')
                      run: ./publish.sh
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotReplaceTextInsideQuotedScalarStringLiterals() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - if: 'always() && contains(github.event.head_commit.message, ''always()'')'
                      run: ./publish.sh
                    - if: "always() && contains(github.event.head_commit.message, 'always()')"
                      run: ./upload.sh
              """,
            """
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - if: '(success() || failure()) && contains(github.event.head_commit.message, ''always()'')'
                      run: ./publish.sh
                    - if: "(success() || failure()) && contains(github.event.head_commit.message, 'always()')"
                      run: ./upload.sh
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotChangeUnrelatedIfKeys() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              name: Example
              inputs:
                if:
                  description: A regular input named if
                  default: always()
              runs:
                using: composite
                steps:
                  - run: ./build.sh
                    shell: bash
              """,
            spec -> spec.path("action.yml")
          )
        );
    }

    @Test
    void doesNotChangeNonGitHubActionsYaml() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  if: always()
                  steps:
                    - if: always()
                      run: ./build.sh
              """,
            spec -> spec.path("pipeline.yml")
          )
        );
    }
}
