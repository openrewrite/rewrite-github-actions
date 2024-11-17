package org.openrewrite.github;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

public class UseGradleWrapperTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseGradleWrapper());
    }

    @DocumentExample
    @Test
    void simpleGradlew() {
        rewriteRun(
          //language=yaml
          yaml(
            """
                    name: Java CI

                    on: [push]

                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - name: Build with gradle
                            run: gradle test
              """,
            """
                    name: Java CI

                    on: [push]

                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - name: Build with gradle
                            run: ./gradlew test
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void windows() {
        rewriteRun(
          //language=yaml
          yaml(
            """
                    name: Java CI

                    on: [push]

                    jobs:
                      build:
                        runs-on: windows-2019
                        steps:
                          - name: Build with gradle
                            run: gradle test
              """,
            """
                    name: Java CI

                    on: [push]

                    jobs:
                      build:
                        runs-on: windows-2019
                        steps:
                          - name: Build with gradle
                            run: gradlew test
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void noChangeIfUnknownRunner() {
        rewriteRun(
          //language=yaml
          yaml(
            """
                    name: Java CI

                    on: [push]

                    jobs:
                      build:
                        runs-on: as-400
                        steps:
                          - name: Build with gradle
                            run: gradle test
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void noChangeIfGradleIsMentionedInSomeOtherWay() {
        rewriteRun(
          //language=yaml
          yaml(
            """
                    name: Java CI

                    on: [push]

                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - name: Build with gradle
                            run: echo I would run gradle test, but I do something else
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

}
