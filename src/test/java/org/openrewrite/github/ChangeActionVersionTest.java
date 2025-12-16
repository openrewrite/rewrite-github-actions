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
import org.openrewrite.Issue;
import org.openrewrite.test.RewriteTest;

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
            source -> source.path(".github/workflows/ci.yaml")
          )
        );
    }

    @Test
    void updateActionVersionYaml() {
        //language=yaml
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

    @Issue("https://github.com/openrewrite/rewrite-github-actions/issues/163")
    @Test
    void updateActionVersionWithWildcard() {
        rewriteRun(
          spec -> spec.recipe(new ChangeActionVersion("actions/nested.*", "v4")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v2
                    - uses: actions/nested/checkout@v2
                    - uses: actions/nested/setup-java@main
              """,
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v2
                    - uses: actions/nested/checkout@v4
                    - uses: actions/nested/setup-java@v4
              """,
            source -> source.path(".github/workflows/ci.yaml")
          )
        );
    }
}
