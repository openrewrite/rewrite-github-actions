/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.github;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class PreferTemurinDistributionsTest implements RewriteTest {

    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreferTemurinDistributions());
    }

    @ParameterizedTest
    // from https://github.com/actions/runner-images
    @ValueSource(strings = {
      "ubuntu-latest",
      "ubuntu-22.04",
      "ubuntu-20.04",
      "ubuntu-18.04",
      "macos-latest",
      "macos-12",
      "macos-11",
      "macos-10.15",
      "windows-latest",
      "windows-2022",
      "windows-2019"
    })
    void preferTemurin(String runner) {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: %s
                  steps:
                    - uses: actions/checkout@v2
                      with:
                        fetch-depth: 0
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "11"
                    - name: set-up-jdk-1
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "adopt"
                        java-version: "17"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "8"
                    - name: build
                      run: ./gradlew build test
              """.formatted(runner),
            """
              jobs:
                build:
                  runs-on: %s
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
                        java-version: "17"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "temurin"
                        java-version: "8"
                    - name: build
                      run: ./gradlew build test
              """.formatted(runner),
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doNotChangeWhenIsAlreadyTemurin() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - name: set-up-example
                      uses: example/example@v1
                      with:
                        distribution: "temurin"
                        java-version: "11"
                    - name: set-up-jdk
                      uses: actions/setup-java@v1
                      with:
                        distribution: "temurin"
                        java-version: "11"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }


    @Test
    void doNotChangeForPrivateRunners() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: myRunner
                  steps:
                    - name: set-up-example
                      uses: example/example@v1
                      with:
                        distribution: "temurin"
                        java-version: "11"
                    - name: set-up-jdk
                      uses: actions/setup-java@v1
                      with:
                        distribution: "temurin"
                        java-version: "11"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }


    @Test
    void doNotChangeWhenThereArePrivateRunners() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: [myRunner,ubuntu-latest]
                  steps:
                    - name: set-up-example
                      uses: example/example@v1
                      with:
                        distribution: "temurin"
                        java-version: "11"
                    - name: set-up-jdk
                      uses: actions/setup-java@v1
                      with:
                        distribution: "temurin"
                        java-version: "11"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }


    @Test
    void testChangesWhenAllAreHosted() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: [ubuntu-latest,macos-latest]
                  steps:
                    - uses: actions/checkout@v2
                      with:
                        fetch-depth: 0
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "11"
                    - name: set-up-jdk-1
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "adopt"
                        java-version: "17"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "8"
                    - name: build
                      run: ./gradlew build test
              """,
            """
              jobs:
                build:
                  runs-on: [ubuntu-latest,macos-latest]
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
                        java-version: "17"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "temurin"
                        java-version: "8"
                    - name: build
                      run: ./gradlew build test
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }


    @Test
    void testOnlyChangesSpecificJobs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: [ubuntu-latest,macos-latest]
                  steps:
                    - uses: actions/checkout@v2
                      with:
                        fetch-depth: 0
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "11"
                    - name: set-up-jdk-1
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "17"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "8"
                    - name: build
                      run: ./gradlew build test
              another-build:
                  runs-on: [myPrivateRunner123,MyPrivateRunner12354]
                  steps:
                    - uses: actions/checkout@v2
                      with:
                        fetch-depth: 0
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "11"
                    - name: set-up-jdk-1
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "adopt"
                        java-version: "17"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "8"
                    - name: build
                      run: ./gradlew build test        
              """,
            """
              jobs:
                build:
                  runs-on: [ubuntu-latest,macos-latest]
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
                        java-version: "17"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "temurin"
                        java-version: "8"
                    - name: build
                      run: ./gradlew build test
              another-build:
                  runs-on: [myPrivateRunner123,MyPrivateRunner12354]
                  steps:
                    - uses: actions/checkout@v2
                      with:
                        fetch-depth: 0
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "11"
                    - name: set-up-jdk-1
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "adopt"
                        java-version: "17"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "8"
                    - name: build
                      run: ./gradlew build test        
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void testChangesMultipleJobs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: [ubuntu-latest,macos-latest]
                  steps:
                    - uses: actions/checkout@v2
                      with:
                        fetch-depth: 0
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "11"
                    - name: set-up-jdk-1
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "17"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "8"
                    - name: build
                      run: ./gradlew build test
              another-build:
                  runs-on: [ubuntu-latest,macos-latest]
                  steps:
                    - uses: actions/checkout@v2
                      with:
                        fetch-depth: 0
                    - name: set-up-jdk-0
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "11"
                    - name: set-up-jdk-1
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "adopt"
                        java-version: "17"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "zulu"
                        java-version: "8"
                    - name: build
                      run: ./gradlew build test        
              """,
            """
              jobs:
                build:
                  runs-on: [ubuntu-latest,macos-latest]
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
                        java-version: "17"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "temurin"
                        java-version: "8"
                    - name: build
                      run: ./gradlew build test
              another-build:
                  runs-on: [ubuntu-latest,macos-latest]
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
                        java-version: "17"
                    - name: set-up-jdk-2
                      uses: actions/setup-java@v2.3.0
                      with:
                        distribution: "temurin"
                        java-version: "8"
                    - name: build
                      run: ./gradlew build test        
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

}
