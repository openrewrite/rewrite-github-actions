/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

import static org.openrewrite.yaml.Assertions.yaml;

class ChangeActionTest implements RewriteTest {
    @DocumentExample
    @Test
    void updateActionVersion() {
        rewriteRun(
          spec -> spec.recipe(new ChangeAction(
            "gradle/wrapper-validation-action",
            "gradle/actions/wrapper-validation",
            "v3")),
          //language=yaml
          yaml(
            """
              jobs:
                deploy:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Checkout
                      uses: actions/checkout@v4
                    - name: Validate wrapper
                      uses: gradle/wrapper-validation-action@v2
              """,
            """
              jobs:
                deploy:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Checkout
                      uses: actions/checkout@v4
                    - name: Validate wrapper
                      uses: gradle/actions/wrapper-validation@v3
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void setupGradleYamlRecipe() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.github.gradle.RenameGradleBuildActionToSetupGradle"),
          //language=yaml
          yaml(
            """
              jobs:
                deploy:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Checkout
                      uses: actions/checkout@v4
                    - uses: gradle/gradle-build-action@v3
              """,
            """
              jobs:
                deploy:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Checkout
                      uses: actions/checkout@v4
                    - uses: gradle/actions/setup-gradle@v3
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }
}
