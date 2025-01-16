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
