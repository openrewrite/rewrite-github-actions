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

class SetupJavaAdoptOpenJDKToTemurinTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SetupJavaAdoptOpenJDKToTemurin());
    }

    @DocumentExample
    @Test
    void actionsSetupJavaAdoptOpenJDKToTemurin() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - uses: actions/checkout@v2
                      with:
                        fetch-depth: 0
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "adopt"
                        java-version: "11"
                    - name: set-up-jdk-1
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "adopt-hotspot"
                        java-version: "11"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "adopt-openj9"
                        java-version: "11"
                    - name: build
                      run: ./gradlew build test
              """,
            """
              jobs:
                build:
                  steps:
                    - uses: actions/checkout@v2
                      with:
                        fetch-depth: 0
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "temurin"
                        java-version: "11"
                    - name: set-up-jdk-1
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "temurin"
                        java-version: "11"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "adopt-openj9"
                        java-version: "11"
                    - name: build
                      run: ./gradlew build test
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void replaceUnderMultipleJobNames() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - uses: actions/checkout@v2
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "adopt"
                        java-version: "11"
                    - name: build
                      run: ./gradlew build test
                publish-snapshots:
                  needs: [build]
                  steps:
                    - uses: actions/checkout@v2
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2
                      with:
                        distribution: "adopt"
                        java-version: "11"
                    - name: build
                      run: ./gradlew snapshot publish
              """,
            """
              jobs:
                build:
                  steps:
                    - uses: actions/checkout@v2
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "temurin"
                        java-version: "11"
                    - name: build
                      run: ./gradlew build test
                publish-snapshots:
                  needs: [build]
                  steps:
                    - uses: actions/checkout@v2
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2
                      with:
                        distribution: "temurin"
                        java-version: "11"
                    - name: build
                      run: ./gradlew snapshot publish
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void onlyReplaceStepsWithUsesActionsSetupJava() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - name: set-up-example
                      uses: example/example@v1
                      with:
                        distribution: "adopt"
                        java-version: "11"
                    - name: set-up-jdk
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "adopt"
                        java-version: "11"
              """,
            """
              jobs:
                build:
                  steps:
                    - name: set-up-example
                      uses: example/example@v1
                      with:
                        distribution: "adopt"
                        java-version: "11"
                    - name: set-up-jdk
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "temurin"
                        java-version: "11"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doNotChangedWhenNoMatches() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - name: set-up-example
                      uses: example/example@v1
                      with:
                        distribution: "adopt"
                        java-version: "11"
                    - name: set-up-jdk
                      uses: actions/setup-java@v1
                      with:
                        distribution: "adopt"
                        java-version: "11"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
