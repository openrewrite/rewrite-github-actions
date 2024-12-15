/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

import static org.openrewrite.yaml.Assertions.yaml;

class SetupJavaUpgradeJavaVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SetupJavaUpgradeJavaVersion(21));
    }

    @DocumentExample
    @Test
    void updatesOldMajorVersion() {
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
                    - name: set-up-temurin-jdk
                      uses: actions/setup-java@v2.3.0
                      with:
                        java-version: "11"
                        distribution: temurin
                    - name: set-up-zulu-jdk
                      uses: actions/setup-java@v3
                      with:
                        java-version: "19"
                        distribution: zulu
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
                    - name: set-up-temurin-jdk
                      uses: actions/setup-java@v2.3.0
                      with:
                        java-version: "21"
                        distribution: temurin
                    - name: set-up-zulu-jdk
                      uses: actions/setup-java@v3
                      with:
                        java-version: "21"
                        distribution: zulu
                    - name: build
                      run: ./gradlew build test
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void updatesOldPatchVersion() {
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
                    - name: set-up-jdk
                      uses: actions/setup-java@v2.3.0
                      with:
                        java-version: "11.0.17"
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
                    - name: set-up-jdk
                      uses: actions/setup-java@v2.3.0
                      with:
                        java-version: "21"
                    - name: build
                      run: ./gradlew build test
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void updatesOldPatchVersionWithMetadata() {
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
                    - name: set-up-jdk
                      uses: actions/setup-java@v2.3.0
                      with:
                        java-version: "15.0.0-ea.2"
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
                    - name: set-up-jdk
                      uses: actions/setup-java@v2.3.0
                      with:
                        java-version: "21"
                    - name: build
                      run: ./gradlew build test
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotUpdateSameVersion() {
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
                    - name: set-up-jdk
                      uses: actions/setup-java@v2.3.0
                      with:
                        java-version: "21.0.0"
                    - name: build
                      run: ./gradlew build test
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotUpdateNewerVersion() {
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
                    - name: set-up-jdk
                      uses: actions/setup-java@v2.3.0
                      with:
                        java-version: "22.0.0"
                    - name: build
                      run: ./gradlew build test
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotUpdateGarbageVersion() {
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
                    - name: set-up-jdk
                      uses: actions/setup-java@v2.3.0
                      with:
                        java-version: "{11}"
                    - name: build
                      run: ./gradlew build test
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doesNotUpdateVersionInOtherActions() {
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
                        java-version: "11"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
