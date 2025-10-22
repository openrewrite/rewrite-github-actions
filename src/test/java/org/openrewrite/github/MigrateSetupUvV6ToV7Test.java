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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class MigrateSetupUvV6ToV7Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.github.MigrateSetupUvV6ToV7");
    }

    @DocumentExample
    @Test
    void upgradesVersionFromV6ToV7() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - uses: astral-sh/setup-uv@v6
                      with:
                        version: "0.5.13"
              """,
            """
              jobs:
                build:
                  steps:
                    - uses: astral-sh/setup-uv@v7
                      with:
                        version: "0.5.13"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void removesDeprecatedServerUrlInput() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - uses: astral-sh/setup-uv@v6
                      with:
                        version: "0.5.13"
                        server-url: https://custom-server.example.com
              """,
            """
              jobs:
                build:
                  steps:
                    - uses: astral-sh/setup-uv@v7
                      with:
                        version: "0.5.13"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void handlesV6WithoutServerUrl() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - uses: astral-sh/setup-uv@v6
              """,
            """
              jobs:
                build:
                  steps:
                    - uses: astral-sh/setup-uv@v7
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void handlesFullVersionFormat() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - uses: astral-sh/setup-uv@v6.1.2
                      with:
                        version: "latest"
                        server-url: https://example.com
              """,
            """
              jobs:
                build:
                  steps:
                    - uses: astral-sh/setup-uv@v7
                      with:
                        version: "latest"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotChangeV7() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - uses: astral-sh/setup-uv@v7
                      with:
                        version: "latest"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotChangeOtherActions() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - uses: actions/setup-python@v5
                      with:
                        python-version: "3.11"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void handlesMultipleSteps() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v6
                      with:
                        version: "0.5.13"
                        server-url: https://example.com
                    - run: uv pip install -r requirements.txt
              """,
            """
              jobs:
                build:
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v7
                      with:
                        version: "0.5.13"
                    - run: uv pip install -r requirements.txt
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void preservesOtherInputs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - uses: astral-sh/setup-uv@v6
                      with:
                        version: "0.5.13"
                        enable-cache: true
                        cache-dependency-glob: "**/requirements.txt"
                        server-url: https://example.com
              """,
            """
              jobs:
                build:
                  steps:
                    - uses: astral-sh/setup-uv@v7
                      with:
                        version: "0.5.13"
                        enable-cache: true
                        cache-dependency-glob: "**/requirements.txt"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
