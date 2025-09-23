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

class AnonymousJobsRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AnonymousJobsRecipe());
    }

    @Test
    @DocumentExample
    void shouldFlagJobWithoutName() {
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
                  build:
                    name: Build application
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "building"
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  ~~(Job has no name. Add a descriptive name to make it easier to identify in workflow runs.)~~>test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                  build:
                    name: Build application
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "building"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagMultipleAnonymousJobs() {
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
                  lint:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "linting"
                  build:
                    name: Build application
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "building"
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  ~~(Job has no name. Add a descriptive name to make it easier to identify in workflow runs.)~~>test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                  ~~(Job has no name. Add a descriptive name to make it easier to identify in workflow runs.)~~>lint:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "linting"
                  build:
                    name: Build application
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "building"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagWorkflowWithName() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    name: Run tests
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - run: npm test
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagJobWithName() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    name: Run unit tests
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - run: npm test
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagJobWithoutNameInWorkflowWithoutName() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                on: push
                jobs:
                  ~~(Job has no name. Add a descriptive name to make it easier to identify in workflow runs.)~~>test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldIgnoreReusableWorkflowJobs() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  call-reusable:
                    uses: ./.github/workflows/reusable.yml
                    with:
                      environment: production
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
                    image: node:18
                    command: npm test
                """,
                sourceSpecs -> sourceSpecs.path("docker-compose.yml")
            )
        );
    }
}