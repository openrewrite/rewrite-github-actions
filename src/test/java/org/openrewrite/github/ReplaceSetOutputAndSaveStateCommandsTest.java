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
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class ReplaceSetOutputAndSaveStateCommandsTest implements RewriteTest {

    @DocumentExample
    @Test
    void replaceSetOutputAndSaveState() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetOutputAndSaveStateCommands()),
          //language=yaml
          yaml(
            """
              name: ci
              on:
                push:
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Save state
                      id: state
                      run: echo "::save-state name=isPost::true"
                    - name: Set output
                      run: echo "::set-output name=version::1.2.3"
                    - name: Read output
                      run: echo "version is ${{ steps.state.outputs.version }}"
              """,
            """
              name: ci
              on:
                push:
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Save state
                      id: state
                      run: echo "isPost=true" >> $GITHUB_STATE
                    - name: Set output
                      run: echo "version=1.2.3" >> $GITHUB_OUTPUT
                    - name: Read output
                      run: echo "version is ${{ steps.state.outputs.version }}"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void replaceSingleQuotedCommands() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetOutputAndSaveStateCommands()),
          //language=yaml
          yaml(
            """
              name: ci
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Set output
                      run: echo '::set-output name=matrix::["a", "b"]'
              """,
            """
              name: ci
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Set output
                      run: echo 'matrix=["a", "b"]' >> $GITHUB_OUTPUT
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void replaceValueContainingExpression() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetOutputAndSaveStateCommands()),
          //language=yaml
          yaml(
            """
              name: ci
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Set output
                      run: echo "::set-output name=sha::${{ github.sha }}"
              """,
            """
              name: ci
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Set output
                      run: echo "sha=${{ github.sha }}" >> $GITHUB_OUTPUT
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void replaceSeveralCommandsInOneBlockScalar() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetOutputAndSaveStateCommands()),
          //language=yaml
          yaml(
            """
              name: ci
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Set outputs
                      run: |
                        echo "::set-output name=one::1"
                        echo "::save-state name=two::2"
              """,
            """
              name: ci
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Set outputs
                      run: |
                        echo "one=1" >> $GITHUB_OUTPUT
                        echo "two=2" >> $GITHUB_STATE
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void usesEnvPrefixForPowerShellStep() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetOutputAndSaveStateCommands()),
          //language=yaml
          yaml(
            """
              name: ci
              jobs:
                build:
                  runs-on: windows-latest
                  steps:
                    - name: Set output
                      shell: pwsh
                      run: echo "::set-output name=version::1.2.3"
              """,
            """
              name: ci
              jobs:
                build:
                  runs-on: windows-latest
                  steps:
                    - name: Set output
                      shell: pwsh
                      run: echo "version=1.2.3" >> $env:GITHUB_OUTPUT
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void usesEnvPrefixForJobDefaultsShell() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetOutputAndSaveStateCommands()),
          //language=yaml
          yaml(
            """
              name: ci
              jobs:
                build:
                  runs-on: windows-latest
                  defaults:
                    run:
                      shell: powershell
                  steps:
                    - name: Set output
                      run: echo "::set-output name=version::1.2.3"
              """,
            """
              name: ci
              jobs:
                build:
                  runs-on: windows-latest
                  defaults:
                    run:
                      shell: powershell
                  steps:
                    - name: Set output
                      run: echo "version=1.2.3" >> $env:GITHUB_OUTPUT
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void leaveUnrelatedRunCommandsUnchanged() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetOutputAndSaveStateCommands()),
          //language=yaml
          yaml(
            """
              name: ci
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Greet
                      run: echo "hello world"
                    - name: Already migrated
                      run: echo "version=1.2.3" >> $GITHUB_OUTPUT
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void otherFilesAreIgnored() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetOutputAndSaveStateCommands()),
          //language=yaml
          yaml(
            """
              name: ci
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Set output
                      run: echo "::set-output name=version::1.2.3"
              """,
            spec -> spec.path("workflows/ci.yml")
          )
        );
    }
}
