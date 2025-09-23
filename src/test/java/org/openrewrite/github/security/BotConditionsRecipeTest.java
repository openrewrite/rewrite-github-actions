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

class BotConditionsRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new BotConditionsRecipe());
    }

    @DocumentExample
    @Test
    void shouldFlagSpoofableActorNameCheck() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    if: github.actor == 'dependabot[bot]'
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    ~~(Bot actor name check is spoofable. Consider using actor_id instead for more secure bot validation.)~~>if: github.actor == 'dependabot[bot]'
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagSpoofableActorInStepCondition() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Dependabot step
                        if: github.actor == 'dependabot[bot]'
                        run: echo "Running for dependabot"
                """,
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Dependabot step
                        ~~(Bot actor name check is spoofable. Consider using actor_id instead for more secure bot validation.)~~>if: github.actor == 'dependabot[bot]'
                        run: echo "Running for dependabot"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagTriggeringActorCheck() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    if: github.triggering_actor == 'renovate[bot]'
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    ~~(Bot actor name check is spoofable. Consider using actor_id instead for more secure bot validation.)~~>if: github.triggering_actor == 'renovate[bot]'
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagPullRequestSenderCheck() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    if: github.event.pull_request.sender.login == 'dependabot[bot]'
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    ~~(Bot actor name check is spoofable. Consider using actor_id instead for more secure bot validation.)~~>if: github.event.pull_request.sender.login == 'dependabot[bot]'
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagContainsCheck() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    if: contains(github.actor, 'bot')
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    ~~(Bot actor check using contains() is unreliable and spoofable. Use exact actor_id comparison instead.)~~>if: contains(github.actor, 'bot')
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagKnownBotActorIdAsString() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    if: github.actor_id == '49699333'
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    ~~(Using string comparison for actor_id. Consider using numeric comparison for better reliability.)~~>if: github.actor_id == '49699333'
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagSecureActorIdCheck() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    if: github.actor_id == 49699333
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagPullRequestSenderIdCheck() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    if: github.event.pull_request.sender.id == 49699333
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagNonBotConditions() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    if: github.actor == 'octocat'
                    steps:
                      - uses: actions/checkout@v4
                      - name: Regular condition
                        if: github.ref == 'refs/heads/main'
                        run: echo "On main branch"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldFlagComplexBotExpression() {
        rewriteRun(
            yaml(
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    if: github.actor == 'dependabot[bot]' || github.actor == 'renovate[bot]'
                    steps:
                      - uses: actions/checkout@v4
                """,
                """
                name: Test Workflow
                on: pull_request
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    ~~(Bot actor name check is spoofable. Consider using actor_id instead for more secure bot validation.)~~>if: github.actor == 'dependabot[bot]' || github.actor == 'renovate[bot]'
                    steps:
                      - uses: actions/checkout@v4
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldIgnoreNonWorkflowFiles() {
        rewriteRun(
            yaml(
                """
                version: '3.8'
                services:
                  app:
                    environment:
                      - ACTOR=dependabot[bot]
                """,
                sourceSpecs -> sourceSpecs.path("docker-compose.yml")
            )
        );
    }
}
