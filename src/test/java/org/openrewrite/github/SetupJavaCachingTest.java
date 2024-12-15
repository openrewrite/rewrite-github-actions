/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.openrewrite.yaml.Assertions.yaml;

class SetupJavaCachingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SetupJavaCaching());
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "./gradlew build -> gradle",
      "./mvnw install -> maven"
    })
    void setupJavaCachingGradle(String buildTool) {
        rewriteRun(
          //language=yaml
          yaml("""
              jobs:
                build:
                  steps:
                    - uses: actions/checkout
                    - uses: actions/setup-java
                      with:
                        distribution: 'temurin'
                        java-version: '11'
                    - run: %s
                    - name: setup-cache
                      uses: actions/cache@v2.1.6
                      with:
                        path: '~/.gradle/caches'
              """.formatted(buildTool.split("->")[0].trim()),
            """
              jobs:
                build:
                  steps:
                    - uses: actions/checkout
                    - uses: actions/setup-java
                      with:
                        distribution: 'temurin'
                        java-version: '11'
                        cache: '%s'
                    - run: %s
              """.formatted(
              buildTool.split("->")[1].trim(),
              buildTool.split("->")[0].trim()
            ),
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
