package org.openrewrite.github

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import org.openrewrite.yaml.Assertions
import java.nio.file.Path

class SetupJavaUpgradeJavaVersionTest : RewriteTest {
    override fun defaults(spec: RecipeSpec) {
        spec.recipe(SetupJavaUpgradeJavaVersion(17))
    }

    @Test
    fun actionsSetupJavaVersionUpdates() = rewriteRun(
        Assertions.yaml(
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
                      java-version: "11"
                  - name: set-up-jdk-1
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "11.0.17"
                  - name: set-up-jdk-2
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "17"
                  - name: set-up-jdk-3
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "17.0.4+11"
                  - name: set-up-jdk-4
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "18"
                  - name: build
                    run: ./gradlew build test
        """,
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
                      java-version: "17"
                  - name: set-up-jdk-1
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "17"
                  - name: set-up-jdk-2
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "17"
                  - name: set-up-jdk-3
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "17.0.4+11"
                  - name: set-up-jdk-4
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "18"
                  - name: build
                    run: ./gradlew build test
        """
        ) { spec ->
            spec.path(".github/workflows/ci.yml")
        }
    )

    @Test
    fun doesNotChangeVersionInOtherActions(@TempDir tempDir: Path) = rewriteRun(
        Assertions.yaml(
            """
                    jobs:
                      build:
                        steps:
                          - name: set-up-example
                            uses: example/example@v1
                            with:
                              java-version: "11"
                """
        ) { spec ->
            spec.path(".github/workflows/ci.yml")
        }
    )
}