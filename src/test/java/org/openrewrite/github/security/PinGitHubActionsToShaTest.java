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

class PinGitHubActionsToShaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PinGitHubActionsToSha(false, null));
    }

    @DocumentExample
    @Test
    void shouldPinThirdPartyActionFromStaticMap() {
        rewriteRun(
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: codecov/codecov-action@v4
                      name: Upload coverage
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: codecov/codecov-action@e28ff129e5465c2c0dcc6f003fc735cb6ae0c673
                      name: Upload coverage
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldNotPinOfficialActionsByDefault() {
        rewriteRun(
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                      name: Checkout
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldPinOfficialActionsWhenOptedIn() {
        rewriteRun(
          spec -> spec.recipe(new PinGitHubActionsToSha(true, null)),
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                      name: Checkout
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@b4ffde65f46336ab88eb53be808477a3936bae11
                      name: Checkout
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldNotPinGitHubOrgByDefault() {
        rewriteRun(
          yaml(
            """
              name: Security
              on: push
              jobs:
                scan:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: github/codeql-action/init@v3
                      name: Init CodeQL
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/security.yml")
          )
        );
    }

    @Test
    void shouldPinGitHubOrgWhenOptedIn() {
        rewriteRun(
          spec -> spec.recipe(new PinGitHubActionsToSha(true, null)),
          yaml(
            """
              name: Security
              on: push
              jobs:
                scan:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: github/codeql-action/init@v3
                      name: Init CodeQL
              """,
            """
              name: Security
              on: push
              jobs:
                scan:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: github/codeql-action/init@1b1aada464948af03b950897e5eb522f92603cc2
                      name: Init CodeQL
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/security.yml")
          )
        );
    }

    @Test
    void shouldNotModifyAlreadyPinnedAction() {
        rewriteRun(
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: codecov/codecov-action@e28ff129e5465c2c0dcc6f003fc735cb6ae0c673
                      name: Upload coverage
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldSkipLocalActions() {
        rewriteRun(
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: ./local-action
                      name: Run local
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldSkipDockerReferences() {
        rewriteRun(
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: docker://alpine:latest
                      name: Run in Docker
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldPinMultipleThirdPartyActions() {
        rewriteRun(
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                      name: Checkout
                    - uses: docker/setup-buildx-action@v3
                      name: Setup Buildx
                    - uses: docker/build-push-action@v5
                      name: Build image
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                      name: Checkout
                    - uses: docker/setup-buildx-action@988b5a0280414f521407ac0e6c68267159f40517
                      name: Setup Buildx
                    - uses: docker/build-push-action@4a13e500e55cf31b7a5d59a38ab2040ab0f42f56
                      name: Build image
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldIgnoreNonWorkflowFiles() {
        rewriteRun(
          yaml(
            """
              config:
                uses: codecov/codecov-action@v4
              """,
            sourceSpecs -> sourceSpecs.path("config.yml")
          )
        );
    }

    @Test
    void shouldPinActionWithSubpath() {
        rewriteRun(
          spec -> spec.recipe(new PinGitHubActionsToSha(false, null)),
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: gradle/actions/setup-gradle@v3
                      name: Setup Gradle
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: gradle/actions/setup-gradle@d9336dac04dea2507a617466bc058a3def92b18b
                      name: Setup Gradle
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldMixPinnedAndUnpinnedActions() {
        rewriteRun(
          spec -> spec.recipe(new PinGitHubActionsToSha(true, null)),
          yaml(
            """
              name: Full Pipeline
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                      name: Checkout
                    - uses: codecov/codecov-action@e28ff129e5465c2c0dcc6f003fc735cb6ae0c673
                      name: Already pinned
                    - uses: ./local-action
                      name: Local
                    - uses: docker/login-action@v3
                      name: Docker login
              """,
            """
              name: Full Pipeline
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@b4ffde65f46336ab88eb53be808477a3936bae11
                      name: Checkout
                    - uses: codecov/codecov-action@e28ff129e5465c2c0dcc6f003fc735cb6ae0c673
                      name: Already pinned
                    - uses: ./local-action
                      name: Local
                    - uses: docker/login-action@343f7c4344506bcbf9b4de18042ae17996df046d
                      name: Docker login
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }
}
