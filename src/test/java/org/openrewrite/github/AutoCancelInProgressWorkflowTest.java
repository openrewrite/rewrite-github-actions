/*
 * Copyright 2025 the original author or authors.
 *
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

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
