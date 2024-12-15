/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.openrewrite.yaml.Assertions.yaml;

class ReplaceRunnersTest implements RewriteTest {

    @DocumentExample
    @Test
    void replaceRunners() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceRunners(
            "build",
            List.of("ubuntu-latest", "MyRunner")
          )),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                other:
                  runs-on: ubuntu-latest
              """,
            """
              jobs:
                build:
                  runs-on: [ubuntu-latest, MyRunner]
                other:
                  runs-on: ubuntu-latest
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }


    @Test
    void dontReplaceRunnersWithInvalidJob() {
        rewriteRun(
          spec -> spec.recipe(new
            ReplaceRunners(
            "SomeOtherJob",
            List.of("ubuntu-latest", "MyRunner")
          )),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                other:
                  runs-on: ubuntu-latest
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
