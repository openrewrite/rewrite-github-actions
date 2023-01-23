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
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import org.openrewrite.yaml.Assertions.yaml
import java.nio.file.Path

class SetupJavaUpgradeJavaVersionTest : RewriteTest {
    override fun defaults(spec: RecipeSpec) {
        spec.recipe(SetupJavaUpgradeJavaVersion(17))
    }

    @Test
    fun updatesOldMajorVersion() = rewriteRun(
        yaml(
            """
            jobs:
              build:
                steps:
                  - uses: actions/checkout@v2
                    with:
                      fetch-depth: 0
                  - name: set-up-jdk
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "11"
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
                  - name: set-up-jdk
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "17"
                  - name: build
                    run: ./gradlew build test
            """
        ) { spec ->
            spec.path(".github/workflows/ci.yml")
        }
    )

    @Test
    fun updatesOldPatchVersion() = rewriteRun(
        yaml(
            """
            jobs:
              build:
                steps:
                  - uses: actions/checkout@v2
                    with:
                      fetch-depth: 0
                  - name: set-up-jdk
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "11.0.17"
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
                  - name: set-up-jdk
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "17"
                  - name: build
                    run: ./gradlew build test
            """
        ) { spec ->
            spec.path(".github/workflows/ci.yml")
        }
    )

    @Test
    fun updatesOldPatchVersionWithMetadata() = rewriteRun(
        yaml(
            """
            jobs:
              build:
                steps:
                  - uses: actions/checkout@v2
                    with:
                      fetch-depth: 0
                  - name: set-up-jdk
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "15.0.0-ea.2"
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
                  - name: set-up-jdk
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "17"
                  - name: build
                    run: ./gradlew build test
            """
        ) { spec ->
            spec.path(".github/workflows/ci.yml")
        }
    )

    @Test
    fun doesNotUpdateSameVersion() = rewriteRun(
        yaml(
            """
            jobs:
              build:
                steps:
                  - uses: actions/checkout@v2
                    with:
                      fetch-depth: 0
                  - name: set-up-jdk
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "17.0.0"
                  - name: build
                    run: ./gradlew build test
            """,
        ) { spec ->
            spec.path(".github/workflows/ci.yml")
        }
    )

    @Test
    fun doesNotUpdateNewerVersion() = rewriteRun(
        yaml(
            """
            jobs:
              build:
                steps:
                  - uses: actions/checkout@v2
                    with:
                      fetch-depth: 0
                  - name: set-up-jdk
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "18.0.0"
                  - name: build
                    run: ./gradlew build test
            """,
        ) { spec ->
            spec.path(".github/workflows/ci.yml")
        }
    )

    @Test
    fun doesNotUpdateGarbageVersion() = rewriteRun(
        yaml(
            """
            jobs:
              build:
                steps:
                  - uses: actions/checkout@v2
                    with:
                      fetch-depth: 0
                  - name: set-up-jdk
                    uses: actions/setup-java@v2.3.0
                    with:
                      java-version: "${{ "11" }}"
                  - name: build
                    run: ./gradlew build test
            """,
        ) { spec ->
            spec.path(".github/workflows/ci.yml")
        }
    )


    @Test
    fun doesNotUpdateVersionInOtherActions(@TempDir tempDir: Path) = rewriteRun(
        yaml(
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