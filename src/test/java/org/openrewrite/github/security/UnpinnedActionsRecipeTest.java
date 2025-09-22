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

class UnpinnedActionsRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnpinnedActionsRecipe());
    }

    @Test
    @DocumentExample
    void shouldFlagUnpinnedActionWithTagVersion() {
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
                        name: Checkout code
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Action 'actions/checkout@v4' is not pinned to a commit SHA. Consider pinning to a specific commit for security and reproducibility.)~~>uses: actions/checkout@v4
                        name: Checkout code
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagUnpinnedActionWithBranchName() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@main
                        name: Checkout code
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Action 'actions/checkout@main' is not pinned to a commit SHA. Consider pinning to a specific commit for security and reproducibility.)~~>uses: actions/checkout@main
                        name: Checkout code
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagActionWithNoVersion() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout
                        name: Checkout code
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Action 'actions/checkout' is not pinned to a commit SHA. Consider pinning to a specific commit for security and reproducibility.)~~>uses: actions/checkout
                        name: Checkout code
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagActionPinnedToSHA() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@b4ffde65f46336ab88eb53be808477a3936bae11
                        name: Checkout code
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagLocalAction() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: ./local-action
                        name: Run local action
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagDockerAction() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: docker://alpine:latest
                        name: Run in Docker
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagMultipleUnpinnedActions() {
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
                        name: Checkout code
                      - uses: actions/setup-java@v3
                        name: Setup Java
                      - uses: actions/cache@b4ffde65f46336ab88eb53be808477a3936bae11
                        name: Cache dependencies
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Action 'actions/checkout@v4' is not pinned to a commit SHA. Consider pinning to a specific commit for security and reproducibility.)~~>uses: actions/checkout@v4
                        name: Checkout code
                      - ~~(Action 'actions/setup-java@v3' is not pinned to a commit SHA. Consider pinning to a specific commit for security and reproducibility.)~~>uses: actions/setup-java@v3
                        name: Setup Java
                      - uses: actions/cache@b4ffde65f46336ab88eb53be808477a3936bae11
                        name: Cache dependencies
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldIgnoreUsesInNonWorkflowFiles() {
        rewriteRun(
            yaml(
                """
                some_config:
                  uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path("config.yml")
            )
        );
    }
}