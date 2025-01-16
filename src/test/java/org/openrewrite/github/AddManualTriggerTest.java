/*
 * Copyright 2025 the original author or authors.
 *
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.DocumentExample;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

import static org.openrewrite.yaml.Assertions.yaml;

class AddManualTriggerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath()
          .build()
          .activateRecipes("org.openrewrite.github.AddManualTrigger"));
    }

    @DocumentExample
    @Test
    void manualTrigger() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
              env:
                TEST: 'value'
              """,
            """
              on:
                push:
                  branches:
                    - main
                workflow_dispatch:
              env:
                TEST: 'value'
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
