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

class TemplateInjectionRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TemplateInjectionRecipe());
    }

    @DocumentExample
    @Test
    void shouldFlagPullRequestTitleInjection() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: pull_request_target
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: 'echo "PR Title: ${{ github.event.pull_request.title }}"'
              """,
            """
              name: Test Workflow
              on: pull_request_target
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Potential template injection vulnerability. User-controlled input 'github.event.pull_request.title' used in run command without proper escaping.)~~>run: 'echo "PR Title: ${{ github.event.pull_request.title }}"'
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagPullRequestBodyInjection() {
        rewriteRun(
          yaml(
            """
              name: Test Workflow
              on: pull_request_target
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: |
                        echo "PR Body:"
                        echo "${{ github.event.pull_request.body }}"
              """,
            """
              name: Test Workflow
              on: pull_request_target
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Potential template injection vulnerability. User-controlled input 'github.event.pull_request.body' used in run command without proper escaping.)~~>run: |
                        echo "PR Body:"
                        echo "${{ github.event.pull_request.body }}"
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagIssueCommentInjection() {
        rewriteRun(
          yaml(
            """
              name: Issue Comment
              on: issue_comment
              jobs:
                respond:
                  runs-on: ubuntu-latest
                  steps:
                    - run: 'echo "Comment: ${{ github.event.comment.body }}"'
              """,
            """
              name: Issue Comment
              on: issue_comment
              jobs:
                respond:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Potential template injection vulnerability. User-controlled input 'github.event.comment.body' used in run command without proper escaping.)~~>run: 'echo "Comment: ${{ github.event.comment.body }}"'
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagActionScriptInjection() {
        rewriteRun(
          yaml(
            """
              name: Script Injection
              on: pull_request
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/github-script@v6
                      with:
                        script: |
                          const title = "${{ github.event.pull_request.title }}";
                          console.log(title);
              """,
            """
              name: Script Injection
              on: pull_request
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Potential code injection in script input. User-controlled content in script execution context.)~~>uses: actions/github-script@v6
                      with:
                        ~~(Potential code injection in script. User-controlled input 'github.event.pull_request.title' used in script without proper escaping.)~~>script: |
                          const title = "${{ github.event.pull_request.title }}";
                          console.log(title);
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagHeadRefInjection() {
        rewriteRun(
          yaml(
            """
              name: Branch Injection
              on: pull_request
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: 'git checkout ${{ github.head_ref }}'
              """,
            """
              name: Branch Injection
              on: pull_request
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Potential template injection vulnerability. User-controlled input 'github.head_ref' used in run command without proper escaping.)~~>run: 'git checkout ${{ github.head_ref }}'
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagSafeContexts() {
        rewriteRun(
          yaml(
            """
              name: Safe Contexts
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: 'echo "SHA: ${{ github.sha }}"'
                    - run: 'echo "Ref: ${{ github.ref }}"'
                    - run: 'echo "Repository: ${{ github.repository }}"'
                    - run: 'echo "Actor: ${{ github.actor }}"'
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagSecretsAndVars() {
        rewriteRun(
          yaml(
            """
              name: Secrets and Vars
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: 'echo "Secret: ${{ secrets.MY_SECRET }}"'
                    - run: 'echo "Var: ${{ vars.MY_VAR }}"'
                    - run: 'echo "Input: ${{ inputs.version }}"'
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagStepsOutputInjection() {
        rewriteRun(
          yaml(
            """
              name: Steps Output
              on: pull_request
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - id: user-input
                      run: 'echo "title=${{ github.event.pull_request.title }}" >> $GITHUB_OUTPUT'
                    - run: 'echo "Title from step: ${{ steps.user-input.outputs.title }}"'
              """,
            """
              name: Steps Output
              on: pull_request
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - id: user-input
                      ~~(Potential template injection vulnerability. User-controlled input 'github.event.pull_request.title' used in run command without proper escaping.)~~>run: 'echo "title=${{ github.event.pull_request.title }}" >> $GITHUB_OUTPUT'
                    - ~~(Potential template injection vulnerability. User-controlled input 'steps.user-input.outputs.title' used in run command without proper escaping.)~~>run: 'echo "Title from step: ${{ steps.user-input.outputs.title }}"'
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldFlagComplexExpressionInjection() {
        rewriteRun(
          yaml(
            """
              name: Complex Expression
              on: pull_request
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: 'echo "Processing: ${{ contains(github.event.pull_request.title, ''test'') }}"'
              """,
            """
              name: Complex Expression
              on: pull_request
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - ~~(Potential template injection vulnerability. User-controlled input in complex expression used in run command without proper escaping.)~~>run: 'echo "Processing: ${{ contains(github.event.pull_request.title, ''test'') }}"'
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagSafeEnvironmentVariableUsage() {
        rewriteRun(
          yaml(
            """
              name: Safe Env Usage
              on: pull_request
              jobs:
                test:
                  runs-on: ubuntu-latest
                  env:
                    PR_TITLE: ${{ github.event.pull_request.title }}
                  steps:
                    - run: 'echo "Title: $PR_TITLE"'
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
                  command: 'echo "${{ github.event.pull_request.title }}"'
              """,
            sourceSpecs -> sourceSpecs.path("docker-compose.yml")
          )
        );
    }
}
