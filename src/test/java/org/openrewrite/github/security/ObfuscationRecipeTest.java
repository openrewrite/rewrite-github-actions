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
package org.openrewrite.github.security;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class ObfuscationRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ObfuscationRecipe());
    }

    @DocumentExample
    @Test
    void shouldFlagObfuscatedUsesWithDot() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout/./action@v4
              """,
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Action reference contains obfuscated path components that may hide the actual action being used.)~~>uses: actions/checkout/./action@v4
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagObfuscatedUsesWithDotDot() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: user/repo/../other-action@v1
              """,
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Action reference contains obfuscated path components that may hide the actual action being used.)~~>uses: user/repo/../other-action@v1
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagObfuscatedUsesWithEmptyComponents() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions//checkout@v4
              """,
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Action reference contains obfuscated path components that may hide the actual action being used.)~~>uses: actions//checkout@v4
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagObfuscatedUsesWithMultipleSlashes() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout////action@v4
              """,
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Action reference contains obfuscated path components that may hide the actual action being used.)~~>uses: actions/checkout////action@v4
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagObfuscatedExpressions() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "${{ '}}hello{{$' }}"
              """,
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Contains potentially obfuscated GitHub Actions expressions that may be attempting to hide malicious code.)~~>run: echo "${{ '}}hello{{$' }}"
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagNormalActionReferences() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-node@v3
                      with:
                        node-version: 18
                    - uses: user/action@v1.0.0
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagLocalActions() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: ./local-action
                    - uses: ./.github/actions/custom
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagDockerActions() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: docker://alpine:latest
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagNormalExpressions() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "${{ github.ref }}"
                    - run: echo "Hello ${{ github.actor }}"
                    - name: Use secret
                      run: echo "${{ secrets.TOKEN }}"
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldIgnoreNonWorkflowFiles() {
        rewriteRun(
          yaml(
            """
              version: '3.8'
              services:
                app:
                  image: nginx/..//apache
                  command: echo "${{ weird }}"
              """,
            sourceSpecs -> sourceSpecs.path("docker-compose.yml")
          )
        );
    }
}
