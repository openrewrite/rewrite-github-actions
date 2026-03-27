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
                    - uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238
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
                    - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5
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
                    - uses: github/codeql-action/init@ebcb5b36ded6beda4ceefea6a8bc4cc885255bb3
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
                    - uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238
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
                    - uses: docker/setup-buildx-action@8d2750c68a42422c14e847fe6c8ac0403b4cbd6f
                      name: Setup Buildx
                    - uses: docker/build-push-action@ca052bb54ab0790a636c9b5f226502c73d547a25
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
                    - uses: gradle/actions/setup-gradle@d9c87d481d55275bb5441eef3fe0e46805f9ef70
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
                    - uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238
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
                    - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5
                      name: Checkout
                    - uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238
                      name: Already pinned
                    - uses: ./local-action
                      name: Local
                    - uses: docker/login-action@c94ce9fb468520275223c153574b00df6fe4bcc9
                      name: Docker login
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }
}
