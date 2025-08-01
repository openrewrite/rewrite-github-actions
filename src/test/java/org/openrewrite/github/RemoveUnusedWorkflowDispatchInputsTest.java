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

class RemoveUnusedWorkflowDispatchInputsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnusedWorkflowDispatchInputs());
    }

    @DocumentExample
    @Test
    void removeUnusedInputs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              name: Test Workflow
              on:
                workflow_dispatch:
                  inputs:
                    usedInput:
                      description: 'This input is used'
                      required: true
                    unusedInput:
                      description: 'This input is not used anywhere'
                      required: false
                    anotherUnusedInput:
                      description: 'Also not used'
                      default: 'default-value'

              jobs:
                test-job:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Use input
                      run: echo "Used input - ${{ github.event.inputs.usedInput }}"
                    - name: Another step
                      run: echo "Just a step without input reference"
              """,
            """
              name: Test Workflow
              on:
                workflow_dispatch:
                  inputs:
                    usedInput:
                      description: 'This input is used'
                      required: true

              jobs:
                test-job:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Use input
                      run: echo "Used input - ${{ github.event.inputs.usedInput }}"
                    - name: Another step
                      run: echo "Just a step without input reference"
              """,
            spec -> spec.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void preserveAllUsedInputs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on:
                workflow_dispatch:
                  inputs:
                    input1:
                      description: 'Used in job'
                    input2:
                      description: 'Used in step'
                    input3:
                      description: 'Used in condition'

              jobs:
                job1:
                  runs-on: ubuntu-latest
                  env:
                    VAR1: ${{ github.event.inputs.input1 }}
                  steps:
                    - name: Step 1
                      run: echo "${{ github.event.inputs.input2 }}"
                    - name: Step 2
                      if: github.event.inputs.input3 == 'true'
                      run: echo "Conditional step"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void handleMultilineStringsWithInputReferences() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on:
                workflow_dispatch:
                  inputs:
                    usedInScript:
                      description: 'Used in multiline script'
                    unusedInput:
                      description: 'Not referenced'

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: |
                        echo "Starting deployment"
                        echo "Input value: ${{ github.event.inputs.usedInScript }}"
                        echo "Done"
              """,
            """
              on:
                workflow_dispatch:
                  inputs:
                    usedInScript:
                      description: 'Used in multiline script'

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: |
                        echo "Starting deployment"
                        echo "Input value: ${{ github.event.inputs.usedInScript }}"
                        echo "Done"
              """,
            spec -> spec.path(".github/workflows/deploy.yml")
          )
        );
    }

    @Test
    void handleNoInputs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on:
                push:
                  branches: [main]
                workflow_dispatch:

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "No inputs"
              """,
            spec -> spec.path(".github/workflows/simple.yml")
          )
        );
    }

    @Test
    void removeAllInputsWhenNoneUsed() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on:
                workflow_dispatch:
                  inputs:
                    unused1:
                      description: 'Not used'
                    unused2:
                      description: 'Also not used'

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "No input usage"
              """,
            """
              on:
                workflow_dispatch:
                  inputs:

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "No input usage"
              """,
            spec -> spec.path(".github/workflows/unused.yml")
          )
        );
    }
}
