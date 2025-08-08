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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

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
                    used-input:
                      description: 'This input is used'
                      required: true
                    unused_input:
                      description: 'This input is not used anywhere'
                      required: false
                    anotherUnusedInput:
                      description: 'Also not used'
                      default: 'default-value'
                    usedInGithubActionSyntax:
                      description: 'Used in Github Action syntax'

              jobs:
                test-job:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Use input
                      run: echo "Used input - ${{ github.event.inputs.used-input }}"
                    - name: Another step
                      run: echo "Just a step without input reference"
                    - name: Step 3
                      if: inputs . usedInGithubActionSyntax == 'true'
                      run: echo "Conditional step"
              """,
            """
              name: Test Workflow
              on:
                workflow_dispatch:
                  inputs:
                    used-input:
                      description: 'This input is used'
                      required: true
                    usedInGithubActionSyntax:
                      description: 'Used in Github Action syntax'

              jobs:
                test-job:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Use input
                      run: echo "Used input - ${{ github.event.inputs.used-input }}"
                    - name: Another step
                      run: echo "Just a step without input reference"
                    - name: Step 3
                      if: inputs . usedInGithubActionSyntax == 'true'
                      run: echo "Conditional step"
              """,
            spec -> spec.path(".github/workflows/test.yml")
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
                    usedInScript436:
                      description: 'Used in multiline script'
                    alsoUsedInScript:
                      description: 'Also used in multiline script'
                    unusedInput:
                      description: 'Not referenced'

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: |
                        echo "Starting deployment"
                        echo "Input value: ${{ github.event.inputs.usedInScript436 }}"
                        echo "Another input value: ${{ github.event.inputs.alsoUsedInScript }}"
                        echo "Done"
              """,
            """
              on:
                workflow_dispatch:
                  inputs:
                    usedInScript436:
                      description: 'Used in multiline script'
                    alsoUsedInScript:
                      description: 'Also used in multiline script'

              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: |
                        echo "Starting deployment"
                        echo "Input value: ${{ github.event.inputs.usedInScript436 }}"
                        echo "Another input value: ${{ github.event.inputs.alsoUsedInScript }}"
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
                workflow_dispatch: {}

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

    @Test
    void doNothingWhenWorkflowDispatchIsEmpty() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              on:
                workflow_dispatch:
              """,
            spec -> spec.path(".github/workflows/stub.yml")
          )
        );
    }
}
