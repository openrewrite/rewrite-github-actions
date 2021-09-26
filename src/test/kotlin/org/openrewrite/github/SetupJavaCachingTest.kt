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

import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.openrewrite.Recipe
import org.openrewrite.yaml.YamlRecipeTest
import java.nio.file.Path

class SetupJavaCachingTest : YamlRecipeTest {
    override val recipe: Recipe
        get() = SetupJavaCaching()

    @ParameterizedTest
    @ValueSource(
        strings = [
            "./gradlew build -> gradle",
            "./mvnw install -> maven"
        ]
    )
    fun setupJavaCachingGradle(buildTool: String, @TempDir tempDir: Path) = assertChanged(
        before = tempDir.resolve(".github/workflows/ci.yml").toFile().apply {
            parentFile.mkdirs()
            writeText(//language=yml
                """
                    jobs:
                      build:
                        steps:
                          - uses: actions/checkout
                          - uses: actions/setup-java
                            with:
                              distribution: 'temurin'
                              java-version: '11'
                          - run: ${buildTool.split("->")[0].trim()}
                          - name: setup-cache
                            uses: actions/cache@v2.1.6
                            with:
                              path: '~/.gradle/caches'
                """.trimIndent()
            )
        },
        relativeTo = tempDir,
        after = """
            jobs:
              build:
                steps:
                  - uses: actions/checkout
                  - uses: actions/setup-java
                    with:
                      distribution: 'temurin'
                      java-version: '11'
                      cache: '${buildTool.split("->")[1].trim()}'
                  - run: ${buildTool.split("->")[0].trim()}
        """
    )
}
