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
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class SetupPythonToUvTest implements RewriteTest {

    @DocumentExample
    @Test
    void basicSetupPythonToUv() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, null, null)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.11'
                    - run: pip install -r requirements.txt
                    - run: python -m pytest
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v6
                      with:
                        python-version: '3.11'
                    - run: uv sync
                    - run: uv run pytest
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void setupPythonWithMatrixStrategy() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, null, null)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  strategy:
                    matrix:
                      python-version: ['3.10', '3.11', '3.12']
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-python@v5
                      with:
                        python-version: ${{ matrix.python-version }}
                    - run: pip install -r requirements.txt
                    - run: python -m pytest
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  strategy:
                    matrix:
                      python-version: ['3.10', '3.11', '3.12']
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v6
                      with:
                        python-version: ${{ matrix.python-version }}
                    - run: uv sync
                    - run: uv run pytest
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void setupPythonWithCache() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, null, null)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.11'
                        cache: 'pip'
                    - run: pip install -r requirements.txt
                    - run: python -m pytest
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v6
                      with:
                        python-version: '3.11'
                        enable-cache: 'true'
                    - run: uv sync
                    - run: uv run pytest
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void setupPythonWithVersionFromFile() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, null, null)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-python@v5
                      with:
                        python-version-file: '.python-version'
                    - run: pip install -r requirements.txt
                    - run: python -m pytest
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v6
                      with:
                        python-version-file: '.python-version'
                    - run: uv sync
                    - run: uv run pytest
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void setupPythonWithPyprojectToml() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, null, null)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-python@v5
                      with:
                        python-version-file: 'pyproject.toml'
                    - run: pip install .
                    - run: python -m pytest
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v6
                      with:
                        python-version-file: 'pyproject.toml'
                    - run: uv sync
                    - run: uv run pytest
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void setupPythonWithComplexInstallationSteps() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, null, null)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.11'
                    - run: python -m pip install --upgrade pip
                    - run: pip install -r requirements.txt
                    - run: pip install -e .
                    - run: python -m pytest --cov=src
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v6
                      with:
                        python-version: '3.11'
                    - run: uv sync
                    - run: uv sync
                    - run: uv run pytest --cov=src
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void setupPythonWithCacheDependencies() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, null, null)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.11'
                        cache: 'pip'
                        cache-dependency-path: '**/requirements.txt'
                    - run: pip install -r requirements.txt
                    - run: python -m pytest
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v6
                      with:
                        python-version: '3.11'
                        enable-cache: 'true'
                    - run: uv sync
                    - run: uv run pytest
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void setupPythonMinimalVersion() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, null, null)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-python@v5
                    - run: pip install -r requirements.txt
                    - run: python -m pytest
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v6
                    - run: uv sync
                    - run: uv run pytest
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void setupPythonWithOtherActions() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, null, null)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-node@v4
                      with:
                        node-version: '18'
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.11'
                    - run: pip install -r requirements.txt
                    - run: python -m pytest
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-node@v4
                      with:
                        node-version: '18'
                    - uses: astral-sh/setup-uv@v6
                      with:
                        python-version: '3.11'
                    - run: uv sync
                    - run: uv run pytest
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotChangeNonSetupPythonActions() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, null, null)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-node@v4
                      with:
                        node-version: '18'
                    - run: npm test
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void customUvVersionAndSyncStrategy() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv("v5", "locked", null, null)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.11'
                    - run: pip install -r requirements.txt
                    - run: python -m pytest
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v5
                      with:
                        python-version: '3.11'
                    - run: uv sync --locked
                    - run: uv run pytest
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void disableCommandTransformation() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, false, null)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.11'
                    - run: pip install -r requirements.txt
                    - run: python -m pytest
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v6
                      with:
                        python-version: '3.11'
                    - run: pip install -r requirements.txt
                    - run: python -m pytest
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void disableCacheConversion() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, null, false)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.11'
                        cache: 'pip'
                    - run: pip install -r requirements.txt
                    - run: python -m pytest
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v6
                      with:
                        python-version: '3.11'
                        cache: 'pip'
                    - run: uv sync
                    - run: uv run pytest
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void allOptionsConfigured() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv("v4", "full", true, true)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-python@v5
                      with:
                        python-version: '3.11'
                        cache: 'pip'
                    - run: pip install -r requirements.txt
                    - run: python -m pytest
              """,
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: astral-sh/setup-uv@v4
                      with:
                        python-version: '3.11'
                        enable-cache: 'true'
                    - run: uv sync --all-extras --dev
                    - run: uv run pytest
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void noModifyNodeCache() {
        rewriteRun(
          spec -> spec.recipe(new SetupPythonToUv(null, null, true, true)),
          //language=yaml
          yaml(
            """
              name: Test

              on: [push]

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - name: Use Node.js 18
                      uses: actions/setup-node@v4
                      with:
                        node-version: 18
                        cache: npm
                        cache-dependency-path: ./path-to-repo/package-lock.json
              """,
            source -> source.path(".github/workflows/test.yml")
          )
        );
    }

}
