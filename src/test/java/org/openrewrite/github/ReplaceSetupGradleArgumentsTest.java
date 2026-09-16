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

class ReplaceSetupGradleArgumentsTest implements RewriteTest {

    @DocumentExample
    @Test
    void splitConsecutiveArgumentStepsAndShareOneSetup() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetupGradleArguments()),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Checkout
                      uses: actions/checkout@v4
                    - name: Assemble the project
                      uses: gradle/actions/setup-gradle@v3
                      with:
                        arguments: assemble
                    - name: Run the tests
                      uses: gradle/actions/setup-gradle@v3
                      with:
                        arguments: test
                    - name: Run build in a subdirectory
                      uses: gradle/actions/setup-gradle@v3
                      with:
                        build-root-directory: another-build
                        arguments: build
              """,
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Checkout
                      uses: actions/checkout@v4
                    - name: Setup Gradle
                      uses: gradle/actions/setup-gradle@v3
                    - name: Assemble the project
                      run: ./gradlew assemble
                    - name: Run the tests
                      run: ./gradlew test
                    - name: Run build in a subdirectory
                      working-directory: another-build
                      run: ./gradlew build
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotChangeSetupGradleWithoutArguments() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetupGradleArguments()),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: gradle/actions/setup-gradle@v3
                    - run: ./gradlew build
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void reusesExistingSetupGradleStep() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetupGradleArguments()),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: gradle/actions/setup-gradle@v3
                    - name: Run the tests
                      uses: gradle/actions/setup-gradle@v3
                      with:
                        arguments: test
              """,
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: gradle/actions/setup-gradle@v3
                    - name: Run the tests
                      run: ./gradlew test
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void keepsRemainingSetupInputsOnTheirOwnSetupStep() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetupGradleArguments()),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Assemble
                      uses: gradle/actions/setup-gradle@v3
                      with:
                        gradle-version: '8.14'
                        arguments: assemble
              """,
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Setup Gradle
                      uses: gradle/actions/setup-gradle@v3
                      with:
                        gradle-version: '8.14'
                    - name: Assemble
                      run: ./gradlew assemble
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void migratesGradleBuildAction() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetupGradleArguments()),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Build
                      uses: gradle/gradle-build-action@v3
                      with:
                        arguments: build --scan
              """,
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Setup Gradle
                      uses: gradle/actions/setup-gradle@v3
                    - name: Build
                      run: ./gradlew build --scan
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void preservesIfAndEnvOnTheRunStep() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetupGradleArguments()),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Publish
                      if: github.ref == 'refs/heads/main'
                      uses: gradle/actions/setup-gradle@v3
                      env:
                        ORG_GRADLE_PROJECT_signingKey: ${{ secrets.SIGNING_KEY }}
                      with:
                        arguments: publish
              """,
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Setup Gradle
                      uses: gradle/actions/setup-gradle@v3
                    - name: Publish
                      if: github.ref == 'refs/heads/main'
                      env:
                        ORG_GRADLE_PROJECT_signingKey: ${{ secrets.SIGNING_KEY }}
                      run: ./gradlew publish
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void splitsArgumentsInCompositeAction() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetupGradleArguments()),
          //language=yaml
          yaml(
            """
              name: Build
              runs:
                using: composite
                steps:
                  - name: Build
                    uses: gradle/actions/setup-gradle@v3
                    with:
                      arguments: build
              """,
            """
              name: Build
              runs:
                using: composite
                steps:
                  - name: Setup Gradle
                    uses: gradle/actions/setup-gradle@v3
                  - name: Build
                    run: ./gradlew build
              """,
            source -> source.path(".github/actions/build/action.yml")
          )
        );
    }

    @Test
    void splitsArgumentsIndependentlyPerJob() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetupGradleArguments()),
          //language=yaml
          yaml(
            """
              jobs:
                assemble:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: gradle/actions/setup-gradle@v3
                      with:
                        arguments: assemble
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: gradle/actions/setup-gradle@v3
                      with:
                        arguments: test
              """,
            """
              jobs:
                assemble:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Setup Gradle
                      uses: gradle/actions/setup-gradle@v3
                    - run: ./gradlew assemble
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - name: Setup Gradle
                      uses: gradle/actions/setup-gradle@v3
                    - run: ./gradlew test
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void ignoresUnrelatedWorkflows() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSetupGradleArguments()),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/setup-java@v4
                      with:
                        arguments: not-gradle
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }
}
