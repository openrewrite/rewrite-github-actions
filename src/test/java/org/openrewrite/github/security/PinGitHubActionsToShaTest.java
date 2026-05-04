/*
 * Copyright 2026 the original author or authors.
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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.yaml.Assertions.yaml;

class PinGitHubActionsToShaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PinGitHubActionsToSha(false, null, null));
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
                    - uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238 # v4
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
          spec -> spec.recipe(new PinGitHubActionsToSha(true, null, null)),
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
                    - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4
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
          spec -> spec.recipe(new PinGitHubActionsToSha(true, null, null)),
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
                    - uses: github/codeql-action/init@0daab03d71ff584ef619d027a3fd9146679c5d84 # v3
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
                    - uses: docker/setup-buildx-action@8d2750c68a42422c14e847fe6c8ac0403b4cbd6f # v3
                      name: Setup Buildx
                    - uses: docker/build-push-action@ca052bb54ab0790a636c9b5f226502c73d547a25 # v5
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
          spec -> spec.recipe(new PinGitHubActionsToSha(false, null, null)),
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
                    - uses: gradle/actions/setup-gradle@d9c87d481d55275bb5441eef3fe0e46805f9ef70 # v3
                      name: Setup Gradle
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldPinActionAsLastEntryInMapping() {
        rewriteRun(
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Upload coverage
                      uses: codecov/codecov-action@v4
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Upload coverage
                      uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238 # v4
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldPlaceCommentOnSameLineWhenUsesIsLastEntryAndAnotherStepFollows() {
        rewriteRun(
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Coverage
                      uses: codecov/codecov-action@v4
                    - uses: docker/setup-buildx-action@v3
                      name: Buildx
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Coverage
                      uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238 # v4
                    - uses: docker/setup-buildx-action@8d2750c68a42422c14e847fe6c8ac0403b4cbd6f # v3
                      name: Buildx
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldPlaceCommentOnSameLineWhenTopLevelEntryFollowsJobs() {
        rewriteRun(
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Coverage
                      uses: codecov/codecov-action@v4
              env:
                FOO: bar
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Coverage
                      uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238 # v4
              env:
                FOO: bar
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldReplaceExistingInlineCommentWithVersionTag() {
        rewriteRun(
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: codecov/codecov-action@v4 # uploads coverage
                      name: Coverage
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238 # v4
                      name: Coverage
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldReplaceExistingInlineCommentWhenUsesIsLastEntry() {
        rewriteRun(
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Coverage
                      uses: codecov/codecov-action@v4 # uploads coverage
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Coverage
                      uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238 # v4
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldPreserveCommentOnDifferentLine() {
        rewriteRun(
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    # uploads coverage to codecov.io
                    - uses: codecov/codecov-action@v4
                      name: Coverage
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    # uploads coverage to codecov.io
                    - uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238 # v4
                      name: Coverage
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldPinReusableWorkflowReference() {
        rewriteRun(
          yaml(
            """
              name: Release
              on:
                push:
                  tags: ['v*']
              jobs:
                provenance:
                  permissions:
                    actions: read
                    id-token: write
                    contents: write
                  uses: slsa-framework/slsa-github-generator/.github/workflows/generator_generic_slsa3.yml@v2.1.0
                  with:
                    base64-subjects: ${{ needs.build.outputs.hashes }}
              """,
            """
              name: Release
              on:
                push:
                  tags: ['v*']
              jobs:
                provenance:
                  permissions:
                    actions: read
                    id-token: write
                    contents: write
                  uses: slsa-framework/slsa-github-generator/.github/workflows/generator_generic_slsa3.yml@f7dd8c54c2067bafc12ca7a55595d5ee9b75204a # v2.1.0
                  with:
                    base64-subjects: ${{ needs.build.outputs.hashes }}
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/release.yml")
          )
        );
    }

    @Test
    void shouldOnlyPinAllowListedActions() {
        rewriteRun(
          spec -> spec.recipe(new PinGitHubActionsToSha(false, null,
            Arrays.asList("codecov/codecov-action", "docker/login-action"))),
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: codecov/codecov-action@v4
                      name: Coverage
                    - uses: docker/login-action@v3
                      name: Docker login
                    - uses: docker/setup-buildx-action@v3
                      name: Setup Buildx (not allow-listed)
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238 # v4
                      name: Coverage
                    - uses: docker/login-action@c94ce9fb468520275223c153574b00df6fe4bcc9 # v3
                      name: Docker login
                    - uses: docker/setup-buildx-action@v3
                      name: Setup Buildx (not allow-listed)
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void shouldSupportOrgWildcardInAllowList() {
        rewriteRun(
          spec -> spec.recipe(new PinGitHubActionsToSha(false, null,
            Collections.singletonList("docker/*"))),
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: docker/login-action@v3
                      name: Docker login
                    - uses: docker/setup-buildx-action@v3
                      name: Setup Buildx
                    - uses: codecov/codecov-action@v4
                      name: Not in docker org
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: docker/login-action@c94ce9fb468520275223c153574b00df6fe4bcc9 # v3
                      name: Docker login
                    - uses: docker/setup-buildx-action@8d2750c68a42422c14e847fe6c8ac0403b4cbd6f # v3
                      name: Setup Buildx
                    - uses: codecov/codecov-action@v4
                      name: Not in docker org
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void allowListMatchesActionWithSubpathByOwnerRepo() {
        rewriteRun(
          spec -> spec.recipe(new PinGitHubActionsToSha(false, null,
            Collections.singletonList("gradle/actions"))),
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
                    - uses: gradle/actions/setup-gradle@d9c87d481d55275bb5441eef3fe0e46805f9ef70 # v3
                      name: Setup Gradle
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void allowListWithSubpathPatternIsExact() {
        rewriteRun(
          spec -> spec.recipe(new PinGitHubActionsToSha(false, null,
            Collections.singletonList("gradle/actions/setup-gradle"))),
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: gradle/actions/setup-gradle@v3
                      name: Allowed
                    - uses: gradle/actions/wrapper-validation@v3
                      name: Different subpath, not allow-listed
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: gradle/actions/setup-gradle@d9c87d481d55275bb5441eef3fe0e46805f9ef70 # v3
                      name: Allowed
                    - uses: gradle/actions/wrapper-validation@v3
                      name: Different subpath, not allow-listed
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void allowListPinsOfficialActionWithoutPinOfficialFlag() {
        rewriteRun(
          spec -> spec.recipe(new PinGitHubActionsToSha(false, null,
            Collections.singletonList("actions/checkout"))),
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
                    - uses: actions/setup-java@v4
                      name: Not on allow list
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4
                      name: Checkout
                    - uses: actions/setup-java@v4
                      name: Not on allow list
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void emptyAllowListBehavesAsDefault() {
        rewriteRun(
          spec -> spec.recipe(new PinGitHubActionsToSha(false, null, Collections.emptyList())),
          yaml(
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: codecov/codecov-action@v4
                      name: Coverage
              """,
            """
              name: CI
              on: push
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238 # v4
                      name: Coverage
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void formatRefCommentEmitsTagRefsUnchanged() {
        assertEquals("v4", PinGitHubActionsToSha.formatRefComment("v4"));
        assertEquals("v1.2.3", PinGitHubActionsToSha.formatRefComment("v1.2.3"));
        assertEquals("2.1.0", PinGitHubActionsToSha.formatRefComment("2.1.0"));
        assertEquals("v2024.01", PinGitHubActionsToSha.formatRefComment("v2024.01"));
    }

    @Test
    void formatRefCommentStampsDateOnBranchRefs() {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        assertEquals("main @ " + today, PinGitHubActionsToSha.formatRefComment("main"));
        assertEquals("master @ " + today, PinGitHubActionsToSha.formatRefComment("master"));
        assertEquals("develop @ " + today, PinGitHubActionsToSha.formatRefComment("develop"));
        assertEquals("release/foo @ " + today, PinGitHubActionsToSha.formatRefComment("release/foo"));
    }

    @Test
    void shouldMixPinnedAndUnpinnedActions() {
        rewriteRun(
          spec -> spec.recipe(new PinGitHubActionsToSha(true, null, null)),
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
                    - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4
                      name: Checkout
                    - uses: codecov/codecov-action@b9fd7d16f6d7d1b5d2bec1a2887e65ceed900238
                      name: Already pinned
                    - uses: ./local-action
                      name: Local
                    - uses: docker/login-action@c94ce9fb468520275223c153574b00df6fe4bcc9 # v3
                      name: Docker login
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/ci.yml")
          )
        );
    }
}
