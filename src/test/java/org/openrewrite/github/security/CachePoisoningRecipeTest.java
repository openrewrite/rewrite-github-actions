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

class CachePoisoningRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CachePoisoningRecipe());
    }

    @DocumentExample
    @Test
    void shouldDetectCacheInReleaseWorkflow() {
        rewriteRun(
            yaml(
                """
                on: release
                jobs:
                  publish:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/cache@v4
                        with:
                          path: ~/.cargo
                          key: cargo-${{ hashFiles('Cargo.lock') }}
                      - uses: softprops/action-gh-release@v1
                        with:
                          files: dist/*
                """,
                """
                on: release
                jobs:
                  publish:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - ~~(Action 'actions/cache' uses caching in a workflow that publishes artifacts. This could lead to cache poisoning where malicious content gets cached and included in published artifacts. Consider disabling caching for this step or using read-only cache mode.)~~>uses: actions/cache@v4
                        with:
                          path: ~/.cargo
                          key: cargo-${{ hashFiles('Cargo.lock') }}
                      - uses: softprops/action-gh-release@v1
                        with:
                          files: dist/*
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectCacheInTagPushWorkflow() {
        rewriteRun(
            yaml(
                """
                on:
                  push:
                    tags: ['v*']
                jobs:
                  publish:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/setup-node@v4
                        with:
                          node-version: '18'
                          cache: npm
                      - run: npm ci
                      - run: npm publish
                """,
                """
                on:
                  push:
                    tags: ['v*']
                jobs:
                  publish:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - ~~(Action 'actions/setup-node' uses caching in a workflow that publishes artifacts. This could lead to cache poisoning where malicious content gets cached and included in published artifacts. Consider disabling caching for this step or using read-only cache mode.)~~>uses: actions/setup-node@v4
                        with:
                          node-version: '18'
                          cache: npm
                      - run: npm ci
                      - run: npm publish
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectCacheInReleaseBranchWorkflow() {
        rewriteRun(
            yaml(
                """
                on:
                  push:
                    branches: ['release/*', 'main']
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/setup-go@v4
                        with:
                          go-version: '1.21'
                          cache: true
                      - run: go build
                      - uses: goreleaser/goreleaser-action@v5
                        if: startsWith(github.ref, 'refs/heads/release/')
                """,
                """
                on:
                  push:
                    branches: ['release/*', 'main']
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - ~~(Action 'actions/setup-go' uses caching in a workflow that publishes artifacts. This could lead to cache poisoning where malicious content gets cached and included in published artifacts. Consider disabling caching for this step or using read-only cache mode.)~~>uses: actions/setup-go@v4
                        with:
                          go-version: '1.21'
                          cache: true
                      - run: go build
                      - uses: goreleaser/goreleaser-action@v5
                        if: startsWith(github.ref, 'refs/heads/release/')
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectCacheWithPublisherAction() {
        rewriteRun(
            yaml(
                """
                on: push
                jobs:
                  publish:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/setup-python@v4
                        with:
                          python-version: '3.11'
                          cache: pip
                      - run: pip install build
                      - run: python -m build
                      - uses: pypa/gh-action-pypi-publish@release/v1
                        with:
                          password: ${{ secrets.PYPI_API_TOKEN }}
                """,
                """
                on: push
                jobs:
                  publish:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - ~~(Action 'actions/setup-python' uses caching in a workflow that publishes artifacts. This could lead to cache poisoning where malicious content gets cached and included in published artifacts. Consider disabling caching for this step or using read-only cache mode.)~~>uses: actions/setup-python@v4
                        with:
                          python-version: '3.11'
                          cache: pip
                      - run: pip install build
                      - run: python -m build
                      - uses: pypa/gh-action-pypi-publish@release/v1
                        with:
                          password: ${{ secrets.PYPI_API_TOKEN }}
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectMultipleCacheActions() {
        rewriteRun(
            yaml(
                """
                on: release
                jobs:
                  publish:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/cache@v4
                        with:
                          path: ~/.cargo
                          key: cargo-${{ hashFiles('Cargo.lock') }}
                      - uses: Swatinem/rust-cache@v2
                      - uses: actions/setup-node@v4
                        with:
                          cache: npm
                      - run: cargo build --release
                      - uses: softprops/action-gh-release@v1
                """,
                """
                on: release
                jobs:
                  publish:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - ~~(Action 'actions/cache' uses caching in a workflow that publishes artifacts. This could lead to cache poisoning where malicious content gets cached and included in published artifacts. Consider disabling caching for this step or using read-only cache mode.)~~>uses: actions/cache@v4
                        with:
                          path: ~/.cargo
                          key: cargo-${{ hashFiles('Cargo.lock') }}
                      - ~~(Action 'Swatinem/rust-cache' uses caching in a workflow that publishes artifacts. This could lead to cache poisoning where malicious content gets cached and included in published artifacts. Consider disabling caching for this step or using read-only cache mode.)~~>uses: Swatinem/rust-cache@v2
                      - ~~(Action 'actions/setup-node' uses caching in a workflow that publishes artifacts. This could lead to cache poisoning where malicious content gets cached and included in published artifacts. Consider disabling caching for this step or using read-only cache mode.)~~>uses: actions/setup-node@v4
                        with:
                          cache: npm
                      - run: cargo build --release
                      - uses: softprops/action-gh-release@v1
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagCacheInNonPublishingWorkflow() {
        rewriteRun(
            yaml(
                """
                on:
                  push:
                    branches: [main]
                  pull_request:
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/cache@v4
                        with:
                          path: ~/.cargo
                          key: cargo-${{ hashFiles('Cargo.lock') }}
                      - uses: actions/setup-node@v4
                        with:
                          cache: npm
                      - run: npm test
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagNonCacheActions() {
        rewriteRun(
            yaml(
                """
                on: release
                jobs:
                  publish:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/download-artifact@v4
                      - run: echo "Building release"
                      - uses: softprops/action-gh-release@v1
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldHandleComplexWorkflowStructure() {
        rewriteRun(
            yaml(
                """
                name: Release Workflow
                on:
                  release:
                    types: [published]
                permissions:
                  contents: write
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    strategy:
                      matrix:
                        target: [x86_64-unknown-linux-gnu, aarch64-apple-darwin]
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions-rust-lang/setup-rust-toolchain@v1
                        with:
                          cache: true
                      - run: cargo build --target ${{ matrix.target }}

                  publish:
                    needs: build
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: softprops/action-gh-release@v1
                        with:
                          files: |
                            target/*/release/myapp
                """,
                """
                name: Release Workflow
                on:
                  release:
                    types: [published]
                permissions:
                  contents: write
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    strategy:
                      matrix:
                        target: [x86_64-unknown-linux-gnu, aarch64-apple-darwin]
                    steps:
                      - uses: actions/checkout@v4
                      - ~~(Action 'actions-rust-lang/setup-rust-toolchain' uses caching in a workflow that publishes artifacts. This could lead to cache poisoning where malicious content gets cached and included in published artifacts. Consider disabling caching for this step or using read-only cache mode.)~~>uses: actions-rust-lang/setup-rust-toolchain@v1
                        with:
                          cache: true
                      - run: cargo build --target ${{ matrix.target }}

                  publish:
                    needs: build
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: softprops/action-gh-release@v1
                        with:
                          files: |
                            target/*/release/myapp
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectDockerBuildPushScenario() {
        rewriteRun(
            yaml(
                """
                on:
                  push:
                    tags: ['v*']
                jobs:
                  docker:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: docker/setup-buildx-action@v3
                      - uses: actions/cache@v4
                        with:
                          path: /tmp/.buildx-cache
                          key: buildx-${{ github.sha }}
                      - uses: docker/build-push-action@v5
                        with:
                          push: true
                          tags: myorg/myapp:${{ github.ref_name }}
                """,
                """
                on:
                  push:
                    tags: ['v*']
                jobs:
                  docker:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - ~~(Action 'docker/setup-buildx-action' uses caching in a workflow that publishes artifacts. This could lead to cache poisoning where malicious content gets cached and included in published artifacts. Consider disabling caching for this step or using read-only cache mode.)~~>uses: docker/setup-buildx-action@v3
                      - ~~(Action 'actions/cache' uses caching in a workflow that publishes artifacts. This could lead to cache poisoning where malicious content gets cached and included in published artifacts. Consider disabling caching for this step or using read-only cache mode.)~~>uses: actions/cache@v4
                        with:
                          path: /tmp/.buildx-cache
                          key: buildx-${{ github.sha }}
                      - uses: docker/build-push-action@v5
                        with:
                          push: true
                          tags: myorg/myapp:${{ github.ref_name }}
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }
}
