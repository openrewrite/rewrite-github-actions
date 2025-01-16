/*
 * Copyright 2025 the original author or authors.
 *
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

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
