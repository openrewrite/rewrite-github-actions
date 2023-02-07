/*
 * Copyright 2023 the original author or authors.
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

class ReplaceRunnersTest : RewriteTest {

    @Test
    fun `Replace Runners`(@TempDir tempDir: Path) = rewriteRun(
        { spec -> spec.recipe(
            ReplaceRunners(
                "build",
                listOf("ubuntu-latest", "MyRunner")
            )
        )},
        yaml(
            """
            jobs:
              build:
                runs-on: ubuntu-latest
              other:
                runs-on: ubuntu-latest
            """,
            """
            jobs:
              build:
                runs-on: [ubuntu-latest, MyRunner]
              other:
                runs-on: ubuntu-latest
            """
        ) { spec ->
            spec.path(".github/workflows/ci.yml")
        }
    )


    @Test
    fun `It should not replace Runners for an invalid Job`(@TempDir tempDir: Path) = rewriteRun(
        { spec -> spec.recipe(
            ReplaceRunners(
                "SomeOtherJob",
                listOf("ubuntu-latest", "MyRunner")
            )
        )},
        yaml(
            """
            jobs:
              build:
                runs-on: ubuntu-latest
              other:
                runs-on: ubuntu-latest
            """
        ) { spec ->
            spec.path(".github/workflows/ci.yml")
        }
    )
}
