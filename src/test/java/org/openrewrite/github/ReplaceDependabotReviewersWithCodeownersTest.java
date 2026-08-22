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

import static org.openrewrite.test.SourceSpecs.other;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

class ReplaceDependabotReviewersWithCodeownersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceDependabotReviewersWithCodeowners(null));
    }

    @DocumentExample
    @Test
    void migrateReviewersToNewCodeownersFile() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  schedule:
                    interval: weekly
                  reviewers:
                    - acme/backend
                - package-ecosystem: npm
                  directory: /frontend
                  schedule:
                    interval: weekly
                  reviewers:
                    - acme/frontend
              """,
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  schedule:
                    interval: weekly
                - package-ecosystem: npm
                  directory: /frontend
                  schedule:
                    interval: weekly
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          text(
            null,
            """
              # Reviewers migrated from the Dependabot configuration
              /pom.xml @acme/backend
              /frontend/package.json @acme/frontend
              /frontend/package-lock.json @acme/frontend
              /frontend/yarn.lock @acme/frontend
              /frontend/pnpm-lock.yaml @acme/frontend
              """,
            spec -> spec.path(".github/CODEOWNERS")
          )
        );
    }

    @Test
    void githubActionsMapsToWorkflowsDirectory() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  reviewers:
                    - acme/devops
              """,
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          text(
            null,
            """
              # Reviewers migrated from the Dependabot configuration
              /.github/workflows/ @acme/devops
              """,
            spec -> spec.path(".github/CODEOWNERS")
          )
        );
    }

    @Test
    void appendToExistingCodeowners() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  reviewers:
                    - acme/backend
              """,
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          text(
            """
              * @acme/everyone
              """,
            """
              * @acme/everyone

              # Reviewers migrated from the Dependabot configuration
              /pom.xml @acme/backend
              """,
            spec -> spec.path("CODEOWNERS")
          )
        );
    }

    @Test
    void doNotDuplicatePatternAlreadyOwned() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  reviewers:
                    - acme/backend
              """,
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          text(
            """
              /pom.xml @someone/else
              """,
            spec -> spec.path("CODEOWNERS")
          )
        );
    }

    @Test
    void individualReviewersArePrefixed() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: gomod
                  directory: /
                  reviewers:
                    - octocat
                    - "@hubot"
              """,
            """
              version: 2
              updates:
                - package-ecosystem: gomod
                  directory: /
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          text(
            null,
            """
              # Reviewers migrated from the Dependabot configuration
              /go.mod @octocat @hubot
              /go.sum @octocat @hubot
              """,
            spec -> spec.path(".github/CODEOWNERS")
          )
        );
    }

    @Test
    void multipleDirectories() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: cargo
                  directories:
                    - /crates/one
                    - /crates/two
                  reviewers:
                    - acme/rust
              """,
            """
              version: 2
              updates:
                - package-ecosystem: cargo
                  directories:
                    - /crates/one
                    - /crates/two
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          text(
            null,
            """
              # Reviewers migrated from the Dependabot configuration
              /crates/one/Cargo.toml @acme/rust
              /crates/one/Cargo.lock @acme/rust
              /crates/two/Cargo.toml @acme/rust
              /crates/two/Cargo.lock @acme/rust
              """,
            spec -> spec.path(".github/CODEOWNERS")
          )
        );
    }

    @Test
    void unknownEcosystemIsLeftAlone() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: some-future-ecosystem
                  directory: /
                  reviewers:
                    - acme/backend
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void unknownEcosystemKeepsItsReviewersWhileOthersMigrate() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: some-future-ecosystem
                  directory: /
                  reviewers:
                    - acme/future
                - package-ecosystem: maven
                  directory: /
                  reviewers:
                    - acme/backend
              """,
            """
              version: 2
              updates:
                - package-ecosystem: some-future-ecosystem
                  directory: /
                  reviewers:
                    - acme/future
                - package-ecosystem: maven
                  directory: /
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          text(
            null,
            """
              # Reviewers migrated from the Dependabot configuration
              /pom.xml @acme/backend
              """,
            spec -> spec.path(".github/CODEOWNERS")
          )
        );
    }

    @Test
    void noReviewersIsNoChange() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  schedule:
                    interval: weekly
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void codeownersPathIsConfigurable() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceDependabotReviewersWithCodeowners("CODEOWNERS")),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  reviewers:
                    - acme/backend
              """,
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          text(
            null,
            """
              # Reviewers migrated from the Dependabot configuration
              /pom.xml @acme/backend
              """,
            spec -> spec.path("CODEOWNERS")
          )
        );
    }

    @Test
    void reviewersInFlowSequence() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  reviewers: [acme/backend, acme/platform]
              """,
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          text(
            null,
            """
              # Reviewers migrated from the Dependabot configuration
              /pom.xml @acme/backend @acme/platform
              """,
            spec -> spec.path(".github/CODEOWNERS")
          )
        );
    }

    @Test
    void multiEcosystemConfigurationFromIssue() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:

              - package-ecosystem: gradle
                directory: "/"
                schedule:
                  interval: weekly
                reviewers:
                  - "kafbat/backend"
                open-pull-requests-limit: 10

              - package-ecosystem: docker
                directory: "/api"
                schedule:
                  interval: weekly
                reviewers:
                  - "kafbat/backend"
                open-pull-requests-limit: 10

              - package-ecosystem: npm
                directory: "/frontend"
                schedule:
                  interval: weekly
                reviewers:
                  - "kafbat/frontend"
                open-pull-requests-limit: 10

              - package-ecosystem: "github-actions"
                directory: "/"
                schedule:
                  interval: weekly
                reviewers:
                  - "kafbat/devops"
                open-pull-requests-limit: 10
              """,
            """
              version: 2
              updates:

              - package-ecosystem: gradle
                directory: "/"
                schedule:
                  interval: weekly
                open-pull-requests-limit: 10

              - package-ecosystem: docker
                directory: "/api"
                schedule:
                  interval: weekly
                open-pull-requests-limit: 10

              - package-ecosystem: npm
                directory: "/frontend"
                schedule:
                  interval: weekly
                open-pull-requests-limit: 10

              - package-ecosystem: "github-actions"
                directory: "/"
                schedule:
                  interval: weekly
                open-pull-requests-limit: 10
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          text(
            null,
            """
              # Reviewers migrated from the Dependabot configuration
              /build.gradle @kafbat/backend
              /build.gradle.kts @kafbat/backend
              /gradle/libs.versions.toml @kafbat/backend
              /api/Dockerfile @kafbat/backend
              /frontend/package.json @kafbat/frontend
              /frontend/package-lock.json @kafbat/frontend
              /frontend/yarn.lock @kafbat/frontend
              /frontend/pnpm-lock.yaml @kafbat/frontend
              /.github/workflows/ @kafbat/devops
              """,
            spec -> spec.path(".github/CODEOWNERS")
          )
        );
    }

    @Test
    void appendsToDotGithubCodeownersWhenRootAlsoExists() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  reviewers:
                    - acme/backend
              """,
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          text(
            """
              * @acme/root
              """,
            spec -> spec.path("CODEOWNERS")
          ),
          text(
            """
              * @acme/everyone
              """,
            """
              * @acme/everyone

              # Reviewers migrated from the Dependabot configuration
              /pom.xml @acme/backend
              """,
            spec -> spec.path(".github/CODEOWNERS")
          )
        );
    }

    @Test
    void keepsReviewersWhenCodeownersCannotBeRead() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  reviewers:
                    - acme/backend
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          other(
            "* @acme/everyone",
            spec -> spec.path(".github/CODEOWNERS")
          )
        );
    }

    @Test
    void secondRunIsANoOp() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  schedule:
                    interval: weekly
              """,
            spec -> spec.path(".github/dependabot.yml")
          ),
          text(
            """
              # Reviewers migrated from the Dependabot configuration
              /pom.xml @acme/backend
              """,
            spec -> spec.path(".github/CODEOWNERS")
          )
        );
    }

    @Test
    void doesNotTouchOtherYamlFiles() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  reviewers:
                    - acme/backend
              """,
            spec -> spec.path("some/other/dependabot-example.yml")
          )
        );
    }
}
