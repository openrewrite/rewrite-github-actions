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
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

import static org.openrewrite.yaml.Assertions.yaml;

class AddManualTriggerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath()
          .build()
          .activateRecipes("org.openrewrite.github.AddManualTrigger"));
    }

    @DocumentExample
    @Test
    void manualTrigger() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
              env:
                TEST: 'value'
              """,
            """
              on:
                push:
                  branches:
                    - main
                workflow_dispatch:
              env:
                TEST: 'value'
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
