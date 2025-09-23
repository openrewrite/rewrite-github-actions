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
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class AutoCancelInProgressWorkflowTest implements RewriteTest {

    @DocumentExample
    @Test
    void useDefaultAccessToken() {
        rewriteRun(
          spec -> spec.recipe(new AutoCancelInProgressWorkflow(null)),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: linux
                  steps:
                    - uses: actions/checkout@v2
              """,
            """
              jobs:
                build:
                  runs-on: linux
                  steps:
                    - uses: styfle/cancel-workflow-action@0.9.1
                      with:
                        access_token: ${{ github.token }}
                    - uses: actions/checkout@v2
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void useUserProvidedAccessToken() {
        rewriteRun(
          spec -> spec.recipe(new AutoCancelInProgressWorkflow("WORKFLOWS_ACCESS_TOKEN")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: linux
                  steps:
                    - uses: actions/checkout@v2
              """,
            """
              jobs:
                build:
                  runs-on: linux
                  steps:
                    - uses: styfle/cancel-workflow-action@0.9.1
                      with:
                        access_token: ${{ secrets.WORKFLOWS_ACCESS_TOKEN }}
                    - uses: actions/checkout@v2
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
