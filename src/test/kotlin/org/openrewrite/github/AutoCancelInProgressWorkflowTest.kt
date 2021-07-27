package org.openrewrite.github

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.Recipe
import org.openrewrite.yaml.YamlRecipeTest
import java.nio.file.Path

class AutoCancelInProgressWorkflowTest : YamlRecipeTest {
    override val recipe: Recipe
        get() = AutoCancelInProgressWorkflow("WORKFLOWS_ACCESS_TOKEN")

    @Test
    fun autoCancel(@TempDir tempDir: Path) = assertChanged(
        before = tempDir.resolve(".github/workflows/ci.yml").toFile().apply {
            parentFile.mkdirs()
            writeText(
                """
                        jobs:
                          build:
                            runs-on: linux
                            steps:
                              - uses: actions/checkout@v2
                    """.trimIndent()
            )
        },
        after = """
            jobs:
              build:
                runs-on: linux
                steps:
                  - uses: styfle/cancel-workflow-action@0.8.0
                    with:
                      access_token: ${'$'}{{secrets.WORKFLOWS_ACCESS_TOKEN}}
                  - uses: actions/checkout@v2
        """
    )
}
