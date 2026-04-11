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

class PreferBlockStyleJobDependenciesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreferBlockStyleJobDependencies());
    }

    @DocumentExample
    @Test
    void convertsFlowToBlockWithMultipleDeps() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                deploy:
                  needs: [build, test, lint]
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
              """,
            """
              on: push
              jobs:
                deploy:
                  needs:
                    - build
                    - test
                    - lint
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotChangeSingleDependency() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                deploy:
                  needs: [build]
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotChangeAlreadyBlockStyle() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                deploy:
                  needs:
                    - build
                    - test
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotChangeScalarNeeds() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                deploy:
                  needs: build
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void convertsMultipleJobs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                deploy:
                  needs: [build, test]
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                release:
                  needs: [build, test, deploy]
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
              """,
            """
              on: push
              jobs:
                deploy:
                  needs:
                    - build
                    - test
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                release:
                  needs:
                    - build
                    - test
                    - deploy
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotAffectRunsOn() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on: push
              jobs:
                build:
                  runs-on: [ubuntu-latest, macos-latest]
                  steps:
                    - uses: actions/checkout@v4
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
