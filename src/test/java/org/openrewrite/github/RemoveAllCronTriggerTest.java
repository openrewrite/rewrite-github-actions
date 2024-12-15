/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

import static org.openrewrite.yaml.Assertions.yaml;

class RemoveAllCronTriggerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveAllCronTriggers());
    }

    @DocumentExample
    @Test
    void removeCronTrigger() {
        rewriteRun(
          //language=yml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
                schedule:
                  - cron: "0 18 * * *"
                  - cron: "0 11 * * *"
              """,
            """
              on:
                push:
                  branches:
                    - main
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
