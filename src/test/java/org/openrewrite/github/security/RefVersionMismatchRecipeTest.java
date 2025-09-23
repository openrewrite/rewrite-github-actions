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

class RefVersionMismatchRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RefVersionMismatchRecipe());
    }

    @DocumentExample
    @Test
    void shouldFlagMismatchedVersionComment() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    # tag=v3
                    - uses: actions/checkout@8ade135a41bc03ea155e62e844d188df1ea18608
              """,
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    # tag=v3
                    - ~~(Action is pinned to a commit SHA but has a version comment that may not match. Verify the comment reflects the actual pinned version.)~~>uses: actions/checkout@8ade135a41bc03ea155e62e844d188df1ea18608
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagVersionCommentPattern() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    # version: v2.8.0
                    - uses: actions/setup-node@b39b52d1213e96004bfcb1c61a8a6fa8ab84f3e8
              """,
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    # version: v2.8.0
                    - ~~(Action is pinned to a commit SHA but has a version comment that may not match. Verify the comment reflects the actual pinned version.)~~>uses: actions/setup-node@b39b52d1213e96004bfcb1c61a8a6fa8ab84f3e8
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagSimpleVersionComment() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    # v4.2.1
                    - uses: actions/upload-artifact@26f96dfa697d77e81fd5907df203aa23a56210a8
              """,
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    # v4.2.1
                    - ~~(Action is pinned to a commit SHA but has a version comment that may not match. Verify the comment reflects the actual pinned version.)~~>uses: actions/upload-artifact@26f96dfa697d77e81fd5907df203aa23a56210a8
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagActionsWithoutVersionComments() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@8ade135a41bc03ea155e62e844d188df1ea18608
                    - name: Setup Node
                      uses: actions/setup-node@b39b52d1213e96004bfcb1c61a8a6fa8ab84f3e8
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagActionsWithTagVersions() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    # This is fine - tag version with comment
                    - uses: actions/checkout@v4
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagNonRepositoryActions() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    # Local action
                    - uses: ./local-action
                    # Docker action
                    - uses: docker://alpine:latest
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
                  # tag=v1.0.0
                  image: myapp@sha256:abc123
              """,
            sourceSpecs -> sourceSpecs.path("docker-compose.yml")
          )
        );
    }
}
