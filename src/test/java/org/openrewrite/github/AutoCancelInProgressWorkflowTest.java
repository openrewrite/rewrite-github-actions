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

class AutoCancelInProgressWorkflowTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AutoCancelInProgressWorkflow());
    }

    @DocumentExample
    @Test
    void addConcurrency() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
              jobs:
                build:
                  runs-on: linux
                  steps:
                    - uses: actions/checkout@v4
              """,
            """
              on:
                push:
                  branches:
                    - main
              concurrency:
                group: ${{ github.workflow }}-${{ github.ref }}
                cancel-in-progress: ${{ github.ref != 'refs/heads/main' }}
              jobs:
                build:
                  runs-on: linux
                  steps:
                    - uses: actions/checkout@v4
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void retainExistingConcurrency() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              concurrency:
                group: ${{ github.workflow }}
                cancel-in-progress: true
              jobs:
                build:
                  runs-on: linux
                  steps:
                    - uses: actions/checkout@v4
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void notAWorkflowFile() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: linux
              """,
            spec -> spec.path("src/main/resources/application.yml")
          )
        );
    }
}
