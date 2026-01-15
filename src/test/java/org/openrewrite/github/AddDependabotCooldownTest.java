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
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.yaml.Assertions.yaml;

class AddDependabotCooldownTest implements RewriteTest {

    @DocumentExample
    @Test
    void addCooldownWithDefaultDays() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotCooldown(null, null, null, null, null, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: daily
                - package-ecosystem: maven
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
                  cooldown:
                    default-days: 7
                - package-ecosystem: maven
                  directory: /
                  schedule:
                    interval: weekly
                  cooldown:
                    default-days: 7
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void addCooldownWithCustomDays() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotCooldown(14, null, null, null, null, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
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
                    interval: weekly
                  cooldown:
                    default-days: 14
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void cooldownAlreadyExists() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotCooldown(7, null, null, null, null, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: daily
                  cooldown:
                    default-days: 7
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void multipleEcosystemsWithExistingOptions() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotCooldown(null, null, null, null, null, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: daily
                  commit-message:
                    prefix: "chore(ci)"
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: weekly
                  open-pull-requests-limit: 10
              """,
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: daily
                  commit-message:
                    prefix: "chore(ci)"
                  cooldown:
                    default-days: 7
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: weekly
                  open-pull-requests-limit: 10
                  cooldown:
                    default-days: 7
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void worksWithDependabotYamlExtension() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotCooldown(null, null, null, null, null, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: monthly
              """,
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: monthly
                  cooldown:
                    default-days: 7
              """,
            spec -> spec.path(".github/dependabot.yaml")
          )
        );
    }

    @Test
    void addsSemverSpecificCooldowns() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotCooldown(7, 14, 7, 3, null, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: weekly
              """,
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: weekly
                  cooldown:
                    default-days: 7
                    semver-major-days: 14
                    semver-minor-days: 7
                    semver-patch-days: 3
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void addsIncludeList() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotCooldown(7, null, null, null,
            List.of("lodash", "react*"), null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: weekly
              """,
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: weekly
                  cooldown:
                    default-days: 7
                    include:
                      - lodash
                      - react*
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void addsExcludeList() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotCooldown(7, null, null, null,
            null, List.of("critical-security-package"))),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: weekly
              """,
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: weekly
                  cooldown:
                    default-days: 7
                    exclude:
                      - critical-security-package
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void addsAllCooldownOptions() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotCooldown(7, 14, 7, 3,
            List.of("lodash", "express"),
            List.of("security-lib"))),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: weekly
              """,
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: weekly
                  cooldown:
                    default-days: 7
                    semver-major-days: 14
                    semver-minor-days: 7
                    semver-patch-days: 3
                    include:
                      - lodash
                      - express
                    exclude:
                      - security-lib
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void addsToMultipleEcosystemsWithDifferentConfigurations() {
        rewriteRun(
          spec -> spec.recipe(new AddDependabotCooldown(7, 14, null, null, null, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: weekly
                - package-ecosystem: pip
                  directory: /
                  schedule:
                    interval: daily
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: monthly
              """,
            """
              version: 2
              updates:
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: weekly
                  cooldown:
                    default-days: 7
                    semver-major-days: 14
                - package-ecosystem: pip
                  directory: /
                  schedule:
                    interval: daily
                  cooldown:
                    default-days: 7
                    semver-major-days: 14
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: monthly
                  cooldown:
                    default-days: 7
                    semver-major-days: 14
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }
}
