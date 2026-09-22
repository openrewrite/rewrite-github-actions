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
package org.openrewrite.github;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class AddDependabotOpenPullRequestsLimitTest implements RewriteTest {

    @DocumentExample
    @Test
    void addLimitToEveryUpdateEntry() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotOpenPullRequestsLimit(10, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: daily
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: weekly
              """,
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: daily
                  open-pull-requests-limit: 10
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: weekly
                  open-pull-requests-limit: 10
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void replacesExistingLimit() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotOpenPullRequestsLimit(3, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: weekly
                  open-pull-requests-limit: 10
              """,
            """
              version: 2
              updates:
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: weekly
                  open-pull-requests-limit: 3
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void limitAlreadySet() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotOpenPullRequestsLimit(5, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: weekly
                  open-pull-requests-limit: 5
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void zeroDisablesVersionUpdates() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotOpenPullRequestsLimit(0, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: daily
              """,
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: daily
                  open-pull-requests-limit: 0
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void onlyMatchingPackageEcosystem() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotOpenPullRequestsLimit(0, "gradle")),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: daily
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: weekly
              """,
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: daily
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: weekly
                  open-pull-requests-limit: 0
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void worksWithDependabotYamlExtension() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotOpenPullRequestsLimit(20, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: pip
                  directory: /
                  schedule:
                    interval: monthly
              """,
            """
              version: 2
              updates:
                - package-ecosystem: pip
                  directory: /
                  schedule:
                    interval: monthly
                  open-pull-requests-limit: 20
              """,
            spec -> spec.path(".github/dependabot.yaml")
          )
        );
    }

    @Test
    void otherDependabotFilesAreIgnored() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotOpenPullRequestsLimit(10, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: gradle
                  directory: /
              """,
            spec -> spec.path("config/dependabot.yml")
          )
        );
    }
}
