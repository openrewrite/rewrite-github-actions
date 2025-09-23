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

class SelfHostedRunnerRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SelfHostedRunnerRecipe());
    }

    @DocumentExample
    @Test
    void shouldFlagSelfHostedRunner() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: [self-hosted, linux, x64]
                  steps:
                    - uses: actions/checkout@v4
              """,
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  ~~(Uses self-hosted runner which may have security implications in public repositories. Ensure runners are ephemeral and properly isolated.)~~>runs-on: [self-hosted, linux, x64]
                  steps:
                    - uses: actions/checkout@v4
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagSelfHostedRunnerAsString() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: self-hosted
                  steps:
                    - uses: actions/checkout@v4
              """,
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  ~~(Uses self-hosted runner which may have security implications in public repositories. Ensure runners are ephemeral and properly isolated.)~~>runs-on: self-hosted
                  steps:
                    - uses: actions/checkout@v4
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagExpressionThatMayExpandToSelfHosted() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ${{ matrix.runner }}
                  strategy:
                    matrix:
                      runner: [ubuntu-latest, self-hosted]
                  steps:
                    - uses: actions/checkout@v4
              """,
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  ~~(Expression may expand to self-hosted runner. Verify that self-hosted runners are properly secured.)~~>runs-on: ${{ matrix.runner }}
                  strategy:
                    matrix:
                      runner: [ubuntu-latest, self-hosted]
                  steps:
                    - uses: actions/checkout@v4
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagGitHubHostedRunners() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                test-windows:
                  runs-on: windows-latest
                  steps:
                    - uses: actions/checkout@v4
                test-macos:
                  runs-on: macos-latest
                  steps:
                    - uses: actions/checkout@v4
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagRunsOnWithMatrixButNoSelfHosted() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ${{ matrix.os }}
                  strategy:
                    matrix:
                      os: [ubuntu-latest, windows-latest, macos-latest]
                  steps:
                    - uses: actions/checkout@v4
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
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
                test:
                  image: self-hosted
                  command: npm test
              """,
            sourceSpecs -> sourceSpecs.path("docker-compose.yml")
          )
        );
    }
}
