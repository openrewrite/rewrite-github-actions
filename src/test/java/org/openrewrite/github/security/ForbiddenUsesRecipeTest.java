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

import java.util.Arrays;

import static org.openrewrite.yaml.Assertions.yaml;

class ForbiddenUsesRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ForbiddenUsesRecipe());
    }

    @Test
    @DocumentExample
    void shouldFlagDangerousAction() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v1
                        name: Checkout code
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Action 'actions/checkout@v1' is known to have security vulnerabilities. Consider upgrading to a more recent version or using an alternative.)~~>uses: actions/checkout@v1
                        name: Checkout code
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagMultipleDangerousActions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v2
                        name: Checkout code
                      - uses: actions/setup-node@v1
                        name: Setup Node
                      - uses: actions/checkout@v4
                        name: Safe checkout
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Action 'actions/checkout@v2' is known to have security vulnerabilities. Consider upgrading to a more recent version or using an alternative.)~~>uses: actions/checkout@v2
                        name: Checkout code
                      - ~~(Action 'actions/setup-node@v1' is known to have security vulnerabilities. Consider upgrading to a more recent version or using an alternative.)~~>uses: actions/setup-node@v1
                        name: Setup Node
                      - uses: actions/checkout@v4
                        name: Safe checkout
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagSuspiciousActionPattern() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: suspicious-org/download-and-run@v1
                        name: Run suspicious action
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Action 'suspicious-org/download-and-run@v1' contains suspicious pattern 'download-and-run'. Review this action carefully for potential security risks.)~~>uses: suspicious-org/download-and-run@v1
                        name: Run suspicious action
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagSingleCharacterOwner() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: x/some-action@v1
                        name: Suspicious action
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Action 'x/some-action@v1' is from a single-character organization 'x' which may be suspicious. Verify the action's authenticity.)~~>uses: x/some-action@v1
                        name: Suspicious action
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagLocalActions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: ./local-action
                        name: Run local action
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagDockerActions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: docker://alpine:latest
                        name: Run in Docker
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagSafeActions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                        name: Checkout code
                      - uses: actions/setup-node@v4
                        name: Setup Node
                      - uses: github/super-linter@v4
                        name: Run linter
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldIgnoreUsesInNonWorkflowFiles() {
        rewriteRun(
            yaml(
                """
                some_config:
                  uses: actions/checkout@v1
                """,
                sourceSpecs -> sourceSpecs.path("config.yml")
            )
        );
    }

    @Test
    void shouldFlagAdditionalDangerousActions() {
        rewriteRun(
            spec -> spec.recipe(new ForbiddenUsesRecipe(
                Arrays.asList("custom-org/dangerous-action@v1", "another-org/risky-action@v2"),
                null
            )),
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: custom-org/dangerous-action@v1
                        name: Custom dangerous action
                      - uses: actions/checkout@v4
                        name: Safe action
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Action 'custom-org/dangerous-action@v1' is known to have security vulnerabilities. Consider upgrading to a more recent version or using an alternative.)~~>uses: custom-org/dangerous-action@v1
                        name: Custom dangerous action
                      - uses: actions/checkout@v4
                        name: Safe action
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagAdditionalSuspiciousPatterns() {
        rewriteRun(
            spec -> spec.recipe(new ForbiddenUsesRecipe(
                null,
                Arrays.asList("crypto-miner", "backdoor")
            )),
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: evil-org/crypto-miner-action@v1
                        name: Suspicious action
                      - uses: actions/checkout@v4
                        name: Safe action
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Action 'evil-org/crypto-miner-action@v1' contains suspicious pattern 'crypto-miner'. Review this action carefully for potential security risks.)~~>uses: evil-org/crypto-miner-action@v1
                        name: Suspicious action
                      - uses: actions/checkout@v4
                        name: Safe action
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagCombinedAdditionalOptions() {
        rewriteRun(
            spec -> spec.recipe(new ForbiddenUsesRecipe(
                Arrays.asList("custom-org/dangerous-action@v1"),
                Arrays.asList("malware")
            )),
            yaml(
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: custom-org/dangerous-action@v1
                        name: Custom dangerous action
                      - uses: bad-org/malware-tool@v1
                        name: Suspicious pattern action
                      - uses: actions/checkout@v1
                        name: Built-in dangerous action
                """,
                """
                name: Test Workflow
                on: push
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - ~~(Action 'custom-org/dangerous-action@v1' is known to have security vulnerabilities. Consider upgrading to a more recent version or using an alternative.)~~>uses: custom-org/dangerous-action@v1
                        name: Custom dangerous action
                      - ~~(Action 'bad-org/malware-tool@v1' contains suspicious pattern 'malware'. Review this action carefully for potential security risks.)~~>uses: bad-org/malware-tool@v1
                        name: Suspicious pattern action
                      - ~~(Action 'actions/checkout@v1' is known to have security vulnerabilities. Consider upgrading to a more recent version or using an alternative.)~~>uses: actions/checkout@v1
                        name: Built-in dangerous action
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }
}