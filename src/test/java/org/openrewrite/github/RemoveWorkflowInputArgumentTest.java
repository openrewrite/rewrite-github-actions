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

class RemoveWorkflowInputArgumentTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveWorkflowInputArgument(
            "org/repo/.github/workflows/myWorkflow.yml",
            "v1.2.3",
            "myInputToRemove"
        ));
    }

    @DocumentExample
    @Test
    void removeWorkflowInputArgument() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              name: Caller Workflow
              on:
                push:
                  branches: [main]

              jobs:
                call-workflow:
                  uses: org/repo/.github/workflows/myWorkflow.yml@v1.2.3
                  with:
                    myInputToRemove: "value to remove"
                    keepThisInput: "keep this value"
                    anotherInput: "also keep this"
              """,
            """
              name: Caller Workflow
              on:
                push:
                  branches: [main]

              jobs:
                call-workflow:
                  uses: org/repo/.github/workflows/myWorkflow.yml@v1.2.3
                  with:
                    keepThisInput: "keep this value"
                    anotherInput: "also keep this"
              """,
            spec -> spec.path(".github/workflows/caller.yml")
          )
        );
    }

    @Test
    void removeFromMultipleWorkflowCalls() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              name: Multiple Calls
              on: workflow_dispatch

              jobs:
                first-call:
                  uses: org/repo/.github/workflows/myWorkflow.yml@v1.2.3
                  with:
                    myInputToRemove: "remove this"
                    keepInput: "keep"

                second-call:
                  uses: org/repo/.github/workflows/myWorkflow.yml@v5.0.4
                  with:
                    myInputToRemove: "would not remove this, same workflow, but different version"
                    otherInput: "keep this too"

                different-workflow:
                  uses: org/repo/.github/workflows/differentWorkflow.yml@v1.0.0
                  with:
                    myInputToRemove: "do not remove - different workflow"
                    someInput: "keep"
              """,
            """
              name: Multiple Calls
              on: workflow_dispatch

              jobs:
                first-call:
                  uses: org/repo/.github/workflows/myWorkflow.yml@v1.2.3
                  with:
                    keepInput: "keep"

                second-call:
                  uses: org/repo/.github/workflows/myWorkflow.yml@v5.0.4
                  with:
                    myInputToRemove: "would not remove this, same workflow, but different version"
                    otherInput: "keep this too"

                different-workflow:
                  uses: org/repo/.github/workflows/differentWorkflow.yml@v1.0.0
                  with:
                    myInputToRemove: "do not remove - different workflow"
                    someInput: "keep"
              """,
            spec -> spec.path(".github/workflows/multi.yml")
          )
        );
    }

    @Test
    void handleWorkflowWithNoWith() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              name: No With Section
              on: push

              jobs:
                call-workflow:
                  uses: org/repo/.github/workflows/myWorkflow.yml@v1.2.3
              """,
            spec -> spec.path(".github/workflows/no-with.yml")
          )
        );
    }

    @Test
    void handleLastInputRemoval() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              name: Last Input
              on: push

              jobs:
                call-workflow:
                  uses: org/repo/.github/workflows/myWorkflow.yml@v1.2.3
                  with:
                    myInputToRemove: "only input"
              """,
            """
              name: Last Input
              on: push

              jobs:
                call-workflow:
                  uses: org/repo/.github/workflows/myWorkflow.yml@v1.2.3
              """,
            spec -> spec.path(".github/workflows/last-input.yml")
          )
        );
    }
}
