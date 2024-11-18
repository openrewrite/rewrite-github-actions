/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

class UseGradleWrapperTest implements RewriteTest {
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
