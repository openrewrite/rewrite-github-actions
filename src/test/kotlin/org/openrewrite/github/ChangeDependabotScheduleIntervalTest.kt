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
import org.openrewrite.yaml.YamlRecipeTest
import java.nio.file.Path

class ChangeDependabotScheduleIntervalTest : YamlRecipeTest {
    @Test
    fun `change dependabot schedule interval`(@TempDir tempDir: Path) = assertChanged(
        recipe = ChangeDependabotScheduleInterval("github-actions", "weekly"),
        before = tempDir.resolve(".github/dependabot.yml").toFile().apply {
            parentFile.mkdirs()
            writeText(//language=yml
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
                """.trimIndent()
            )
        },
        relativeTo = tempDir,
        after = """
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
    )

    @Test
    fun `do not change when no matching package-ecosystem`(@TempDir tempDir: Path) = assertUnchanged(
        recipe = ChangeDependabotScheduleInterval("npm", "weekly"),
        before = tempDir.resolve(".github/dependabot.yml").toFile().apply {
            parentFile.mkdirs()
            writeText(//language=yml
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
            )
        },
        relativeTo = tempDir
    )

    @Test
    fun `do not change when configuration already matches`(@TempDir tempDir: Path) = assertUnchanged(
        recipe = ChangeDependabotScheduleInterval("github-actions", "daily"),
        before = tempDir.resolve(".github/dependabot.yml").toFile().apply {
            parentFile.mkdirs()
            writeText(//language=yml
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
                """
            )
        },
        relativeTo = tempDir
    )

}
