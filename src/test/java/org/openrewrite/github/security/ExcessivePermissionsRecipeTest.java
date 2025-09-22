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

class ExcessivePermissionsRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExcessivePermissionsRecipe());
    }

    @Test
    @DocumentExample
    void shouldFlagWriteAllPermissions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                permissions: write-all
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: push
                ~~(Uses 'write-all' permissions which grants excessive access. Consider using specific permissions instead.)~~>permissions: write-all
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagReadAllPermissions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                permissions: read-all
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: push
                ~~(Uses 'read-all' permissions. Consider using specific permissions if only certain resources need to be accessed.)~~>permissions: read-all
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagHighRiskWritePermissions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                permissions:
                  contents: write
                  packages: write
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: push
                ~~(Contains potentially excessive write permissions: contents: write (high risk), packages: write (high risk). Consider whether these permissions are necessary and if they can be scoped more narrowly.)~~>permissions:
                  contents: write
                  packages: write
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagMediumRiskWritePermissions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                permissions:
                  checks: write
                  discussions: write
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: push
                ~~(Contains potentially excessive write permissions: checks: write (medium risk), discussions: write (medium risk). Consider whether these permissions are necessary and if they can be scoped more narrowly.)~~>permissions:
                  checks: write
                  discussions: write
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagJobLevelPermissions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    permissions: write-all
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    ~~(Uses 'write-all' permissions which grants excessive access. Consider using specific permissions instead.)~~>permissions: write-all
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagReadPermissions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                permissions:
                  contents: read
                  packages: read
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagLowRiskWritePermissions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                permissions:
                  statuses: write
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagEmptyPermissions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                permissions: {}
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }
}