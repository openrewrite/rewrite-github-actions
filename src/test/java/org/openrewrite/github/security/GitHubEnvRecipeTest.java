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

class GitHubEnvRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new GitHubEnvRecipe());
    }

    @DocumentExample
    @Test
    void shouldDetectGitHubEnvInPullRequestTarget() {
        rewriteRun(
            yaml(
                """
                on: pull_request_target
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - name: Set environment
                        run: |
                          echo "BRANCH_NAME=${{ github.head_ref }}" >> $GITHUB_ENV
                          echo "Building branch: $BRANCH_NAME"
                """,
                """
                on: pull_request_target
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - name: Set environment
                        ~~(Write to GITHUB_ENV may allow code execution in a workflow with dangerous triggers. This can lead to code injection when the written content includes user-controlled data. Ensure any dynamic content is properly sanitized or avoid writing to environment files in workflows triggered by untrusted events.)~~>run: |
                          echo "BRANCH_NAME=${{ github.head_ref }}" >> $GITHUB_ENV
                          echo "Building branch: $BRANCH_NAME"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectGitHubEnvInWorkflowRun() {
        rewriteRun(
            yaml(
                """
                on:
                  workflow_run:
                    workflows: [CI]
                    types: [completed]
                jobs:
                  process:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Process results
                        run: |
                          STATUS=${{ github.event.workflow_run.conclusion }}
                          echo "WORKFLOW_STATUS=$STATUS" >> $GITHUB_ENV
                """,
                """
                on:
                  workflow_run:
                    workflows: [CI]
                    types: [completed]
                jobs:
                  process:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Process results
                        ~~(Write to GITHUB_ENV may allow code execution in a workflow with dangerous triggers. This can lead to code injection when the written content includes user-controlled data. Ensure any dynamic content is properly sanitized or avoid writing to environment files in workflows triggered by untrusted events.)~~>run: |
                          STATUS=${{ github.event.workflow_run.conclusion }}
                          echo "WORKFLOW_STATUS=$STATUS" >> $GITHUB_ENV
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectGitHubPathUsage() {
        rewriteRun(
            yaml(
                """
                on: pull_request_target
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Add to PATH
                        run: |
                          echo "/custom/bin" >> $GITHUB_PATH
                          echo "PATH updated"
                """,
                """
                on: pull_request_target
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Add to PATH
                        ~~(Write to GITHUB_PATH may allow code execution in a workflow with dangerous triggers. This can lead to code injection when the written content includes user-controlled data. Ensure any dynamic content is properly sanitized or avoid writing to environment files in workflows triggered by untrusted events.)~~>run: |
                          echo "/custom/bin" >> $GITHUB_PATH
                          echo "PATH updated"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectVariousShellSyntax() {
        rewriteRun(
            yaml(
                """
                on: pull_request_target
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Bash syntax
                        run: echo "VAR=value" >> $GITHUB_ENV
                      - name: Quoted bash syntax
                        run: echo "VAR=value" >> "$GITHUB_ENV"
                      - name: Brace expansion
                        run: echo "VAR=value" >> ${GITHUB_ENV}
                      - name: Pipe to tee
                        run: echo "VAR=value" | tee $GITHUB_ENV
                """,
                """
                on: pull_request_target
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Bash syntax
                        ~~(Write to GITHUB_ENV may allow code execution in a workflow with dangerous triggers. This can lead to code injection when the written content includes user-controlled data. Ensure any dynamic content is properly sanitized or avoid writing to environment files in workflows triggered by untrusted events.)~~>run: echo "VAR=value" >> $GITHUB_ENV
                      - name: Quoted bash syntax
                        ~~(Write to GITHUB_ENV may allow code execution in a workflow with dangerous triggers. This can lead to code injection when the written content includes user-controlled data. Ensure any dynamic content is properly sanitized or avoid writing to environment files in workflows triggered by untrusted events.)~~>run: echo "VAR=value" >> "$GITHUB_ENV"
                      - name: Brace expansion
                        ~~(Write to GITHUB_ENV may allow code execution in a workflow with dangerous triggers. This can lead to code injection when the written content includes user-controlled data. Ensure any dynamic content is properly sanitized or avoid writing to environment files in workflows triggered by untrusted events.)~~>run: echo "VAR=value" >> ${GITHUB_ENV}
                      - name: Pipe to tee
                        ~~(Write to GITHUB_ENV may allow code execution in a workflow with dangerous triggers. This can lead to code injection when the written content includes user-controlled data. Ensure any dynamic content is properly sanitized or avoid writing to environment files in workflows triggered by untrusted events.)~~>run: echo "VAR=value" | tee $GITHUB_ENV
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectPowerShellSyntax() {
        rewriteRun(
            yaml(
                """
                on: pull_request_target
                jobs:
                  test:
                    runs-on: windows-latest
                    steps:
                      - name: PowerShell redirect
                        run: echo "VAR=value" >> $env:GITHUB_ENV
                      - name: PowerShell Out-File
                        run: echo "VAR=value" | Out-File -FilePath $env:GITHUB_ENV -Append
                      - name: PowerShell Add-Content
                        run: Add-Content -Path $env:GITHUB_ENV -Value "VAR=value"
                """,
                """
                on: pull_request_target
                jobs:
                  test:
                    runs-on: windows-latest
                    steps:
                      - name: PowerShell redirect
                        ~~(Write to GITHUB_ENV may allow code execution in a workflow with dangerous triggers. This can lead to code injection when the written content includes user-controlled data. Ensure any dynamic content is properly sanitized or avoid writing to environment files in workflows triggered by untrusted events.)~~>run: echo "VAR=value" >> $env:GITHUB_ENV
                      - name: PowerShell Out-File
                        ~~(Write to GITHUB_ENV may allow code execution in a workflow with dangerous triggers. This can lead to code injection when the written content includes user-controlled data. Ensure any dynamic content is properly sanitized or avoid writing to environment files in workflows triggered by untrusted events.)~~>run: echo "VAR=value" | Out-File -FilePath $env:GITHUB_ENV -Append
                      - name: PowerShell Add-Content
                        ~~(Write to GITHUB_ENV may allow code execution in a workflow with dangerous triggers. This can lead to code injection when the written content includes user-controlled data. Ensure any dynamic content is properly sanitized or avoid writing to environment files in workflows triggered by untrusted events.)~~>run: Add-Content -Path $env:GITHUB_ENV -Value "VAR=value"
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagSafeTriggers() {
        rewriteRun(
            yaml(
                """
                on:
                  push:
                    branches: [main]
                  pull_request:
                    branches: [main]
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Set environment (safe)
                        run: echo "BRANCH_NAME=${{ github.ref_name }}" >> $GITHUB_ENV
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldNotFlagWorkflowsWithoutDangerousTriggers() {
        rewriteRun(
            yaml(
                """
                on:
                  schedule:
                    - cron: '0 0 * * *'
                  workflow_dispatch:
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Set environment (safe)
                        run: echo "DAILY_RUN=true" >> $GITHUB_ENV
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldHandleMixedTriggers() {
        rewriteRun(
            yaml(
                """
                on:
                  push:
                    branches: [main]
                  pull_request_target:
                    types: [opened, synchronize]
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Set environment
                        run: echo "TRIGGER=mixed" >> $GITHUB_ENV
                """,
                """
                on:
                  push:
                    branches: [main]
                  pull_request_target:
                    types: [opened, synchronize]
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Set environment
                        ~~(Write to GITHUB_ENV may allow code execution in a workflow with dangerous triggers. This can lead to code injection when the written content includes user-controlled data. Ensure any dynamic content is properly sanitized or avoid writing to environment files in workflows triggered by untrusted events.)~~>run: echo "TRIGGER=mixed" >> $GITHUB_ENV
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }

    @Test
    void shouldDetectComplexScenarios() {
        rewriteRun(
            yaml(
                """
                on: pull_request_target
                jobs:
                  analyze:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                        with:
                          ref: ${{ github.event.pull_request.head.sha }}
                      - name: Extract PR info
                        run: |
                          PR_TITLE="${{ github.event.pull_request.title }}"
                          PR_AUTHOR="${{ github.event.pull_request.user.login }}"
                          echo "PR_TITLE=$PR_TITLE" >> $GITHUB_ENV
                          echo "PR_AUTHOR=$PR_AUTHOR" >> $GITHUB_ENV

                          # This is particularly dangerous as PR title can contain arbitrary content
                          if [[ "$PR_TITLE" == *"urgent"* ]]; then
                            echo "PRIORITY=high" >> $GITHUB_ENV
                          fi
                """,
                """
                on: pull_request_target
                jobs:
                  analyze:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                        with:
                          ref: ${{ github.event.pull_request.head.sha }}
                      - name: Extract PR info
                        ~~(Write to GITHUB_ENV may allow code execution in a workflow with dangerous triggers. This can lead to code injection when the written content includes user-controlled data. Ensure any dynamic content is properly sanitized or avoid writing to environment files in workflows triggered by untrusted events.)~~>run: |
                          PR_TITLE="${{ github.event.pull_request.title }}"
                          PR_AUTHOR="${{ github.event.pull_request.user.login }}"
                          echo "PR_TITLE=$PR_TITLE" >> $GITHUB_ENV
                          echo "PR_AUTHOR=$PR_AUTHOR" >> $GITHUB_ENV

                          # This is particularly dangerous as PR title can contain arbitrary content
                          if [[ "$PR_TITLE" == *"urgent"* ]]; then
                            echo "PRIORITY=high" >> $GITHUB_ENV
                          fi
                """,
                sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
            )
        );
    }
}
