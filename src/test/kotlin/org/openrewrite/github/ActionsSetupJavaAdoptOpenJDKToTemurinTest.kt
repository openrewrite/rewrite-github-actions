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
import org.openrewrite.Recipe
import org.openrewrite.yaml.YamlRecipeTest
import java.nio.file.Path

class ActionsSetupJavaAdoptOpenJDKToTemurinTest : YamlRecipeTest {
    override val recipe: Recipe
        get() = ActionsSetupJavaAdoptOpenJDKToTemurin()

    @Test
    fun actionsSetupJavaAdoptOpenJDKToTemurin(@TempDir tempDir: Path) = assertChanged(
        before = tempDir.resolve(".github/workflows/ci.yml").toFile().apply {
            parentFile.mkdirs()
            writeText(//language=yml
                """
                    jobs:
                      build:
                        steps:
                          - uses: actions/checkout@v2
                            with:
                              fetch-depth: 0
                          - name: set-up-jdk-0
                            uses: actions/setup-java@v2.3.0
                            with:
                              distribution: "adopt"
                              java-version: "11"
                          - name: set-up-jdk-1
                            uses: actions/setup-java@v2.3.0
                            with:
                              distribution: "adopt-hotspot"
                              java-version: "11"
                          - name: set-up-jdk-2
                            uses: actions/setup-java@v2.3.0
                            with:
                              distribution: "adopt-openj9"
                              java-version: "11"
                          - name: build
                            run: ./gradlew build test
                """.trimIndent()
            )
        },
        relativeTo = tempDir,
        after = """
            jobs:
              build:
                steps:
                  - uses: actions/checkout@v2
                    with:
                      fetch-depth: 0
                  - name: set-up-jdk-0
                    uses: actions/setup-java@v2.3.0
                    with:
                      distribution: "temurin"
                      java-version: "11"
                  - name: set-up-jdk-1
                    uses: actions/setup-java@v2.3.0
                    with:
                      distribution: "temurin"
                      java-version: "11"
                  - name: set-up-jdk-2
                    uses: actions/setup-java@v2.3.0
                    with:
                      distribution: "temurin"
                      java-version: "11"
                  - name: build
                    run: ./gradlew build test
        """
    )

    @Test
    fun replaceUnderMultipleJobNames(@TempDir tempDir: Path) = assertChanged(
        before = tempDir.resolve(".github/workflows/ci.yml").toFile().apply {
            parentFile.mkdirs()
            writeText(//language=yml
                """
                    jobs:
                      build:
                        steps:
                          - uses: actions/checkout@v2
                          - name: set-up-jdk-0
                            uses: actions/setup-java@v2.3.0
                            with:
                              distribution: "adopt"
                              java-version: "11"
                          - name: build
                            run: ./gradlew build test
                      publish-snapshots:
                        needs: [build]
                        steps:
                          - uses: actions/checkout@v2
                          - name: set-up-jdk-0
                            uses: actions/setup-java@v2
                            with:
                              distribution: "adopt"
                              java-version: "11"
                          - name: build
                            run: ./gradlew snapshot publish
                """.trimIndent()
            )
        },
        relativeTo = tempDir,
        after = """
            jobs:
              build:
                steps:
                  - uses: actions/checkout@v2
                  - name: set-up-jdk-0
                    uses: actions/setup-java@v2.3.0
                    with:
                      distribution: "temurin"
                      java-version: "11"
                  - name: build
                    run: ./gradlew build test
              publish-snapshots:
                needs: [build]
                steps:
                  - uses: actions/checkout@v2
                  - name: set-up-jdk-0
                    uses: actions/setup-java@v2
                    with:
                      distribution: "temurin"
                      java-version: "11"
                  - name: build
                    run: ./gradlew snapshot publish
        """
    )

    @Test
    fun onlyReplaceStepsWithUsesActionsSetupJava(@TempDir tempDir: Path) = assertChanged(
        before = tempDir.resolve(".github/workflows/ci.yml").toFile().apply {
            parentFile.mkdirs()
            writeText(//language=yml
                """
                    jobs:
                      build:
                        steps:
                          - name: set-up-example
                            uses: example/example@v1
                            with:
                              distribution: "adopt"
                              java-version: "11"
                          - name: set-up-jdk
                            uses: actions/setup-java@v2.3.0
                            with:
                              distribution: "adopt"
                              java-version: "11"
                """.trimIndent()
            )
        },
        relativeTo = tempDir,
        after = """
            jobs:
              build:
                steps:
                  - name: set-up-example
                    uses: example/example@v1
                    with:
                      distribution: "adopt"
                      java-version: "11"
                  - name: set-up-jdk
                    uses: actions/setup-java@v2.3.0
                    with:
                      distribution: "temurin"
                      java-version: "11"
        """
    )

}
