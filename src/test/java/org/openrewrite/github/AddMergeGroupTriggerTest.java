/*
 * Copyright 2026 the original author or authors.
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

class AddMergeGroupTriggerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.github.AddMergeGroupTrigger");
    }

    @DocumentExample
    @Test
    void addsMergeGroupTriggerToPullRequestWorkflow() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
                pull_request:
                  branches:
                    - main
              jobs:
                build:
                  runs-on: ubuntu-latest
              """,
            """
              on:
                push:
                  branches:
                    - main
                pull_request:
                  branches:
                    - main
                merge_group:
              jobs:
                build:
                  runs-on: ubuntu-latest
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void leavesWorkflowWithoutPullRequestTriggerUnchanged() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void leavesExistingMergeGroupTriggerUnchanged() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on:
                pull_request:
                merge_group:
              """,
            source -> source.path(".github/workflows/ci.yaml")
          )
        );
    }

    @Test
    void leavesNonWorkflowFileUnchanged() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on:
                pull_request:
              """,
            source -> source.path("workflow.yml")
          )
        );
    }
}
