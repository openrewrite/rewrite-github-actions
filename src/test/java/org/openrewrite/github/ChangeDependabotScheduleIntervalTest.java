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

import static org.openrewrite.yaml.Assertions.yaml;

class ChangeDependabotScheduleIntervalTest implements RewriteTest {

    @DocumentExample
    @Test
    void changeDependabotScheduleInterval() {
        rewriteRun(
          spec -> spec.recipe(new ChangeDependabotScheduleInterval("github-actions", "weekly")),
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
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: monthly
              """,
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: weekly
                - package-ecosystem: maven
                  directory: /
                  schedule:
                    interval: weekly
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: monthly
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void configureCompleteScheduleAndAddMissingFieldsInOrder() {
        rewriteRun(
          spec -> spec.recipe(new ChangeDependabotScheduleInterval(
            "github-actions", "weekly", "monday", "09:00", "Asia/Tokyo")),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /one
                  schedule:
                    # Keep the interval comment
                    interval: daily # and its suffix
                    day: sunday
                    time: "08:00"
                    timezone: "Europe/Paris"
                - package-ecosystem: maven
                  directory: /
                  schedule:
                    interval: daily
                    day: friday
                    time: "07:00"
                    timezone: "Europe/London"
                - package-ecosystem: github-actions
                  directory: /two
                  schedule:
                    # Keep the timezone comment
                    timezone: "UTC" # and its suffix
              """,
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /one
                  schedule:
                    # Keep the interval comment
                    interval: weekly # and its suffix
                    day: monday
                    time: "09:00"
                    timezone: "Asia/Tokyo"
                - package-ecosystem: maven
                  directory: /
                  schedule:
                    interval: daily
                    day: friday
                    time: "07:00"
                    timezone: "Europe/London"
                - package-ecosystem: github-actions
                  directory: /two
                  schedule:
                    interval: weekly
                    day: monday
                    time: "09:00"
                    # Keep the timezone comment
                    timezone: "Asia/Tokyo" # and its suffix
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void nullScheduleOptionsLeaveExistingFieldsUnchanged() {
        rewriteRun(
          spec -> spec.recipe(new ChangeDependabotScheduleInterval(
            "github-actions", "weekly", null, null, null)),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: daily
                    day: sunday
                    time: "08:00"
                    timezone: "Europe/Paris"
              """,
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: weekly
                    day: sunday
                    time: "08:00"
                    timezone: "Europe/Paris"
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void addMissingScheduleFieldsToYamlFile() {
        rewriteRun(
          spec -> spec.recipeFromYaml("""
            type: specs.openrewrite.org/v1beta/recipe
            name: org.example.ConfigureDependabotSchedule
            displayName: Configure Dependabot schedule
            description: Configure a complete Dependabot schedule.
            recipeList:
              - org.openrewrite.github.ChangeDependabotScheduleInterval:
                  packageEcosystem: github-actions
                  interval: monthly
                  day: tuesday
                  time: "10:30"
                  timezone: America/New_York
            """, "org.example.ConfigureDependabotSchedule"),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule: { interval: daily }
                - package-ecosystem: github-actions
                  directory: /empty
                  schedule: {}
              """,
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: monthly
                    day: tuesday
                    time: "10:30"
                    timezone: "America/New_York"
                - package-ecosystem: github-actions
                  directory: /empty
                  schedule:
                    interval: monthly
                    day: tuesday
                    time: "10:30"
                    timezone: "America/New_York"
              """,
            spec -> spec.path(".github/dependabot.yaml")
          )
        );
    }

    @Test
    void oldDeclarativeConfigurationPreservesExistingFlowMappingOptions() {
        rewriteRun(
          spec -> spec.recipeFromYaml("""
            type: specs.openrewrite.org/v1beta/recipe
            name: org.example.ChangeDependabotInterval
            displayName: Change Dependabot interval
            description: Change only the Dependabot interval.
            recipeList:
              - org.openrewrite.github.ChangeDependabotScheduleInterval:
                  packageEcosystem: github-actions
                  interval: weekly
            """, "org.example.ChangeDependabotInterval"),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule: { interval: daily, day: sunday, time: "08:00", timezone: "Europe/Paris" }
              """,
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule: { interval: weekly, day: sunday, time: "08:00", timezone: "Europe/Paris" }
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void packageEcosystemRegexRemainsSupported() {
        rewriteRun(
          spec -> spec.recipe(new ChangeDependabotScheduleInterval("maven|gradle", "daily")),
          //language=yaml
          yaml(
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  schedule:
                    interval: weekly
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: monthly
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: weekly
              """,
            """
              version: 2
              updates:
                - package-ecosystem: maven
                  directory: /
                  schedule:
                    interval: daily
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: daily
                - package-ecosystem: npm
                  directory: /
                  schedule:
                    interval: weekly
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void noMatchingPackageEcosystem() {
        rewriteRun(
          spec -> spec.recipe(new ChangeDependabotScheduleInterval("npm", "weekly")),
          yaml(
            //language=yml
            """
              version: 2
              updates:
                - package-ecosystem: github-actions
                  directory: /
                  schedule:
                    interval: daily
                    time: "09:00"
                - package-ecosystem: maven
                  directory: /
                  schedule:
                    interval: weekly
                    time: "09:00"
                    timezone: "Asia/Tokyo"
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: monthly
                    day: sunday
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }

    @Test
    void configurationAlreadyMatches() {
        rewriteRun(
          spec -> spec.recipe(new ChangeDependabotScheduleInterval("github-actions", "daily")),
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
                - package-ecosystem: gradle
                  directory: /
                  schedule:
                    interval: monthly
              """,
            spec -> spec.path(".github/dependabot.yml")
          )
        );
    }
}
