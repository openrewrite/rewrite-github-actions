/*
 * Copyright 2023 the original author or authors.
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

import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

import static org.openrewrite.yaml.Assertions.yaml;

class SetupJavaAdoptOpenj9ToSemeruTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SetupJavaAdoptOpenj9ToSemeru());
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
                        distribution: "semeru"
                        java-version: "11"
                    - name: build
                      run: ./gradlew build test
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
