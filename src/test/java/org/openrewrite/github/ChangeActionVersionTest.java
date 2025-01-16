/*
 * Copyright 2025 the original author or authors.
 *
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

import static org.openrewrite.yaml.Assertions.yaml;

class ChangeActionVersionTest implements RewriteTest {
    @DocumentExample
    @Test
    void updateActionVersion() {
        rewriteRun(
          spec -> spec.recipe(new ChangeActionVersion("actions/setup-java", "v4")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v2
                    - uses: actions/setup-java@main
              """,
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v2
                    - uses: actions/setup-java@v4
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void updateActionVersionYaml() {
        rewriteRun(
          spec -> spec.recipeFromYaml("""
            type: specs.openrewrite.org/v1beta/recipe
            name: org.example.SetupJavaV4
            displayName: Exammple
            description: Fix all the things.
            recipeList:
              - org.openrewrite.github.ChangeActionVersion:
                  action: actions/setup-java
                  version: v4
            """, "org.example.SetupJavaV4"),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v2
                    - uses: actions/setup-java@main
              """,
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v2
                    - uses: actions/setup-java@v4
              """,
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }
}
