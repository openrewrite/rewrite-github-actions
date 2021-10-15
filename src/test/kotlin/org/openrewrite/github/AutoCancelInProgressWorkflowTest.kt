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

class AutoCancelInProgressWorkflowTest : YamlRecipeTest {
    @Test
    fun useDefaultAccessToken(@TempDir tempDir: Path) = assertChanged(
        recipe = AutoCancelInProgressWorkflow(null),
        before = tempDir.resolve(".github/workflows/ci.yml").toFile().apply {
            parentFile.mkdirs()
            writeText(//language=yml
                """
                    jobs:
                      build:
                        runs-on: linux
                        steps:
                          - uses: actions/checkout@v2
                """.trimIndent()
            )
        },
        relativeTo = tempDir,
        after = """
            jobs:
              build:
                runs-on: linux
                steps:
                  - uses: styfle/cancel-workflow-action@0.9.1
                    with:
                      access_token: ${'$'}{{ github.token }}
                  - uses: actions/checkout@v2
        """
    )

    @Test
    fun useUserProvidedAccessToken(@TempDir tempDir: Path) = assertChanged(
        recipe = AutoCancelInProgressWorkflow("WORKFLOWS_ACCESS_TOKEN"),
        before = tempDir.resolve(".github/workflows/ci.yml").toFile().apply {
            parentFile.mkdirs()
            writeText(//language=yml
                """
                    jobs:
                      build:
                        runs-on: linux
                        steps:
                          - uses: actions/checkout@v2
                """.trimIndent()
            )
        },
        relativeTo = tempDir,
        after = """
            jobs:
              build:
                runs-on: linux
                steps:
                  - uses: styfle/cancel-workflow-action@0.9.1
                    with:
                      access_token: ${'$'}{{ secrets.WORKFLOWS_ACCESS_TOKEN }}
                  - uses: actions/checkout@v2
        """
    )

}
