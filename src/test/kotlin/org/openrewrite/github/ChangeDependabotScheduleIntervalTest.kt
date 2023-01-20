/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.github

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.test.RewriteTest
import org.openrewrite.yaml.Assertions.yaml
import java.nio.file.Path

class ChangeDependabotScheduleIntervalTest : RewriteTest {

    @Test
    fun `change dependabot schedule interval`(@TempDir tempDir: Path) = rewriteRun(
        { spec -> spec.recipe(ChangeDependabotScheduleInterval("github-actions", "weekly")) },
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
        """
        ) { spec ->
            spec.path(".github/dependabot.yml")
        }
    )

    @Test
    fun `do not change when no matching package-ecosystem`(@TempDir tempDir: Path) = rewriteRun(
        { spec -> spec.recipe(ChangeDependabotScheduleInterval("npm", "weekly")) },
        yaml(//language=yml
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
                """
        ) { spec ->
            spec.path(".github/dependabot.yml")
        }
    )

    @Test
    fun `do not change when configuration already matches`(@TempDir tempDir: Path) = rewriteRun(
        { spec -> spec.recipe(ChangeDependabotScheduleInterval("github-actions", "daily")) },
        yaml("""
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
            """
        ) { spec ->
            spec.path(".github/dependabot.yml")
        }
    )
}
