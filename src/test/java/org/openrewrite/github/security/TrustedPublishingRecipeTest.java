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

class TrustedPublishingRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TrustedPublishingRecipe());
    }

    @DocumentExample
    @Test
    void shouldFlagPyPIPublishWithPassword() {
        rewriteRun(
          yaml(
            """
              name: Publish to PyPI
              on: push
              jobs:
                publish:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: pypa/gh-action-pypi-publish@v1.5.0
                      with:
                        password: ${{ secrets.PYPI_API_TOKEN }}
                        repository-url: https://upload.pypi.org/legacy/
              """,
            """
              name: Publish to PyPI
              on: push
              jobs:
                publish:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Uses manual credentials instead of trusted publishing. Consider using OIDC trusted publishing for better security.)~~>uses: pypa/gh-action-pypi-publish@v1.5.0
                      with:
                        ~~(Manual credential used here)~~>password: ${{ secrets.PYPI_API_TOKEN }}
                        repository-url: https://upload.pypi.org/legacy/
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/publish.yml")
          )
        );
    }

    @Test
    void shouldFlagRubyGemsWithoutTrustedPublishing() {
        rewriteRun(
          yaml(
            """
              name: Publish to RubyGems
              on: push
              jobs:
                publish:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: rubygems/release-gem@v1
                      with:
                        setup-trusted-publisher: false
              """,
            """
              name: Publish to RubyGems
              on: push
              jobs:
                publish:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Uses manual credentials instead of trusted publishing. Consider using OIDC trusted publishing for better security.)~~>uses: rubygems/release-gem@v1
                      with:
                        ~~(Manual credential used here)~~>setup-trusted-publisher: false
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/publish.yml")
          )
        );
    }

    @Test
    void shouldFlagNpmPublishWithAlwaysAuth() {
        rewriteRun(
          yaml(
            """
              name: Publish to npm
              on: push
              jobs:
                publish:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-node@v3
                      with:
                        registry-url: https://registry.npmjs.org
                        always-auth: true
                    - run: npm publish
              """,
            """
              name: Publish to npm
              on: push
              jobs:
                publish:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Uses manual credentials instead of trusted publishing. Consider using OIDC trusted publishing for better security.)~~>uses: actions/setup-node@v3
                      with:
                        registry-url: https://registry.npmjs.org
                        ~~(Manual credential used here)~~>always-auth: true
                    - ~~(Manual publishing command detected. Consider using trusted publishing actions instead.)~~>run: npm publish
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/publish.yml")
          )
        );
    }

    @Test
    void shouldFlagManualPublishCommands() {
        rewriteRun(
          yaml(
            """
              name: Manual Publish
              on: push
              jobs:
                publish:
                  runs-on: ubuntu-latest
                  steps:
                    - run: |
                        pip install twine
                        twine upload dist/*
              """,
            """
              name: Manual Publish
              on: push
              jobs:
                publish:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Manual publishing command detected. Consider using trusted publishing actions instead.)~~>run: |
                        pip install twine
                        twine upload dist/*
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/publish.yml")
          )
        );
    }

    @Test
    void shouldFlagCargoPublish() {
        rewriteRun(
          yaml(
            """
              name: Publish to crates.io
              on: push
              jobs:
                publish:
                  runs-on: ubuntu-latest
                  steps:
                    - run: cargo publish --token ${{ secrets.CARGO_TOKEN }}
              """,
            """
              name: Publish to crates.io
              on: push
              jobs:
                publish:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Manual publishing command detected. Consider using trusted publishing actions instead.)~~>run: cargo publish --token ${{ secrets.CARGO_TOKEN }}
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/publish.yml")
          )
        );
    }

    @Test
    void shouldNotFlagTrustedPublishingSetup() {
        rewriteRun(
          yaml(
            """
              name: Trusted Publishing
              on: push
              permissions:
                id-token: write
              jobs:
                publish:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: pypa/gh-action-pypi-publish@v1.5.0
                      # Uses OIDC trusted publishing, no password needed
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/publish.yml")
          )
        );
    }

    @Test
    void shouldNotFlagRubyGemsTrustedPublishing() {
        rewriteRun(
          yaml(
            """
              name: Publish to RubyGems
              on: push
              jobs:
                publish:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: rubygems/release-gem@v1
                      with:
                        setup-trusted-publisher: true
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/publish.yml")
          )
        );
    }

    @Test
    void shouldNotFlagNonPublishingActions() {
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
                    - run: npm test
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
                  image: node:18
                  command: npm run dev
              """,
            sourceSpecs -> sourceSpecs.path("docker-compose.yml")
          )
        );
    }
}
