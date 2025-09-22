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
package org.openrewrite.github.security;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class InsecureCommandsRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InsecureCommandsRecipe());
    }

    @Test
    @DocumentExample
    void shouldDetectInsecureCommandsAtWorkflowLevel() {
        rewriteRun(
            yaml(
                """
                on: push
                env:
                  ACTIONS_ALLOW_UNSECURE_COMMANDS: true
                  OTHER_VAR: value
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "test"
                """,
                """
                on: push
                env:
                  ~~(Insecure commands are enabled via ACTIONS_ALLOW_UNSECURE_COMMANDS. This allows dangerous workflow commands that can lead to code injection. Remove this environment variable to disable insecure commands.)~~>ACTIONS_ALLOW_UNSECURE_COMMANDS: true
                  OTHER_VAR: value
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo "test"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectInsecureCommandsAtJobLevel() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    env:
                      ACTIONS_ALLOW_UNSECURE_COMMANDS: true
                      DEBUG: enabled
                    steps:
                      - run: echo "test"
                """,
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    env:
                      ~~(Insecure commands are enabled via ACTIONS_ALLOW_UNSECURE_COMMANDS. This allows dangerous workflow commands that can lead to code injection. Remove this environment variable to disable insecure commands.)~~>ACTIONS_ALLOW_UNSECURE_COMMANDS: true
                      DEBUG: enabled
                    steps:
                      - run: echo "test"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectInsecureCommandsAtStepLevel() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Run with insecure commands
                        run: echo "test"
                        env:
                          ACTIONS_ALLOW_UNSECURE_COMMANDS: true
                          STEP_VAR: value
                """,
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Run with insecure commands
                        run: echo "test"
                        env:
                          ~~(Insecure commands are enabled via ACTIONS_ALLOW_UNSECURE_COMMANDS. This allows dangerous workflow commands that can lead to code injection. Remove this environment variable to disable insecure commands.)~~>ACTIONS_ALLOW_UNSECURE_COMMANDS: true
                          STEP_VAR: value
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectVariousTruthyValues() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test1:
                    runs-on: ubuntu-latest
                    env:
                      ACTIONS_ALLOW_UNSECURE_COMMANDS: "true"
                    steps:
                      - run: echo "test1"
                  test2:
                    runs-on: ubuntu-latest
                    env:
                      ACTIONS_ALLOW_UNSECURE_COMMANDS: 1
                    steps:
                      - run: echo "test2"
                  test3:
                    runs-on: ubuntu-latest
                    env:
                      ACTIONS_ALLOW_UNSECURE_COMMANDS: "yes"
                    steps:
                      - run: echo "test3"
                  test4:
                    runs-on: ubuntu-latest
                    env:
                      ACTIONS_ALLOW_UNSECURE_COMMANDS: "on"
                    steps:
                      - run: echo "test4"
                """,
                """
                on: push
                jobs:
                  test1:
                    runs-on: ubuntu-latest
                    env:
                      ~~(Insecure commands are enabled via ACTIONS_ALLOW_UNSECURE_COMMANDS. This allows dangerous workflow commands that can lead to code injection. Remove this environment variable to disable insecure commands.)~~>ACTIONS_ALLOW_UNSECURE_COMMANDS: "true"
                    steps:
                      - run: echo "test1"
                  test2:
                    runs-on: ubuntu-latest
                    env:
                      ~~(Insecure commands are enabled via ACTIONS_ALLOW_UNSECURE_COMMANDS. This allows dangerous workflow commands that can lead to code injection. Remove this environment variable to disable insecure commands.)~~>ACTIONS_ALLOW_UNSECURE_COMMANDS: 1
                    steps:
                      - run: echo "test2"
                  test3:
                    runs-on: ubuntu-latest
                    env:
                      ~~(Insecure commands are enabled via ACTIONS_ALLOW_UNSECURE_COMMANDS. This allows dangerous workflow commands that can lead to code injection. Remove this environment variable to disable insecure commands.)~~>ACTIONS_ALLOW_UNSECURE_COMMANDS: "yes"
                    steps:
                      - run: echo "test3"
                  test4:
                    runs-on: ubuntu-latest
                    env:
                      ~~(Insecure commands are enabled via ACTIONS_ALLOW_UNSECURE_COMMANDS. This allows dangerous workflow commands that can lead to code injection. Remove this environment variable to disable insecure commands.)~~>ACTIONS_ALLOW_UNSECURE_COMMANDS: "on"
                    steps:
                      - run: echo "test4"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagFalsyValues() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test1:
                    runs-on: ubuntu-latest
                    env:
                      ACTIONS_ALLOW_UNSECURE_COMMANDS: false
                    steps:
                      - run: echo "test1"
                  test2:
                    runs-on: ubuntu-latest
                    env:
                      ACTIONS_ALLOW_UNSECURE_COMMANDS: 0
                    steps:
                      - run: echo "test2"
                  test3:
                    runs-on: ubuntu-latest
                    env:
                      ACTIONS_ALLOW_UNSECURE_COMMANDS: "no"
                    steps:
                      - run: echo "test3"
                  test4:
                    runs-on: ubuntu-latest
                    env:
                      ACTIONS_ALLOW_UNSECURE_COMMANDS: "off"
                    steps:
                      - run: echo "test4"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagGitHubExpressions() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    env:
                      ACTIONS_ALLOW_UNSECURE_COMMANDS: ${{ secrets.ENABLE_INSECURE }}
                    steps:
                      - run: echo "test"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagVariablesOutsideEnvContext() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    name: "ACTIONS_ALLOW_UNSECURE_COMMANDS should not be flagged here"
                    steps:
                      - run: |
                          echo "ACTIONS_ALLOW_UNSECURE_COMMANDS=true" # This is just a comment
                          echo "This step mentions ACTIONS_ALLOW_UNSECURE_COMMANDS but not in env"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectMultipleInstances() {
        rewriteRun(
            yaml(
                """
                on: push
                env:
                  ACTIONS_ALLOW_UNSECURE_COMMANDS: true
                  GLOBAL_VAR: value
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    env:
                      ACTIONS_ALLOW_UNSECURE_COMMANDS: 1
                    steps:
                      - name: Step with insecure commands
                        run: echo "test"
                        env:
                          ACTIONS_ALLOW_UNSECURE_COMMANDS: "yes"
                """,
                """
                on: push
                env:
                  ~~(Insecure commands are enabled via ACTIONS_ALLOW_UNSECURE_COMMANDS. This allows dangerous workflow commands that can lead to code injection. Remove this environment variable to disable insecure commands.)~~>ACTIONS_ALLOW_UNSECURE_COMMANDS: true
                  GLOBAL_VAR: value
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    env:
                      ~~(Insecure commands are enabled via ACTIONS_ALLOW_UNSECURE_COMMANDS. This allows dangerous workflow commands that can lead to code injection. Remove this environment variable to disable insecure commands.)~~>ACTIONS_ALLOW_UNSECURE_COMMANDS: 1
                    steps:
                      - name: Step with insecure commands
                        run: echo "test"
                        env:
                          ~~(Insecure commands are enabled via ACTIONS_ALLOW_UNSECURE_COMMANDS. This allows dangerous workflow commands that can lead to code injection. Remove this environment variable to disable insecure commands.)~~>ACTIONS_ALLOW_UNSECURE_COMMANDS: "yes"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldHandleCaseInsensitiveValues() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    env:
                      ACTIONS_ALLOW_UNSECURE_COMMANDS: "TRUE"
                    steps:
                      - run: echo "test"
                """,
                """
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    env:
                      ~~(Insecure commands are enabled via ACTIONS_ALLOW_UNSECURE_COMMANDS. This allows dangerous workflow commands that can lead to code injection. Remove this environment variable to disable insecure commands.)~~>ACTIONS_ALLOW_UNSECURE_COMMANDS: "TRUE"
                    steps:
                      - run: echo "test"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }
}