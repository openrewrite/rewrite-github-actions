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

import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

import static org.openrewrite.yaml.Assertions.yaml;

class ReplaceSecretsTest implements RewriteTest {
    @DocumentExample
    @Test
    void replaceSecretsInWorkflow() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSecrets("OSSRH_S01_USERNAME", "SONATYPE_USERNAME", null)),
          //language=yaml
          yaml(
            """
              name: Publish
              jobs:
                build:
                  secrets:
                    ossrh_username: ${{ secrets.OSSRH_S01_USERNAME }}
                    ossrh_token: ${{ secrets.OSSRH_S01_TOKEN }}
                  steps:
                    - name: Publish to Maven Central
                      env:
                        MAVEN_USERNAME: ${{ secrets.OSSRH_S01_USERNAME }}
              """,
            """
              name: Publish
              jobs:
                build:
                  secrets:
                    ossrh_username: ${{ secrets.SONATYPE_USERNAME }}
                    ossrh_token: ${{ secrets.OSSRH_S01_TOKEN }}
                  steps:
                    - name: Publish to Maven Central
                      env:
                        MAVEN_USERNAME: ${{ secrets.SONATYPE_USERNAME }}
              """,
            source -> source.path(".github/workflows/publish.yml")
          )
        );
    }

    @Test
    void replaceTokenSecret() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSecrets("OSSRH_S01_TOKEN", "SONATYPE_TOKEN", null)),
          //language=yaml
          yaml(
            """
              name: Publish
              jobs:
                build:
                  secrets:
                    ossrh_username: ${{ secrets.OSSRH_S01_USERNAME }}
                    ossrh_token: ${{ secrets.OSSRH_S01_TOKEN }}
                  steps:
                    - name: Publish to Maven Central
                      env:
                        MAVEN_TOKEN: ${{ secrets.OSSRH_S01_TOKEN }}
              """,
            """
              name: Publish
              jobs:
                build:
                  secrets:
                    ossrh_username: ${{ secrets.OSSRH_S01_USERNAME }}
                    ossrh_token: ${{ secrets.SONATYPE_TOKEN }}
                  steps:
                    - name: Publish to Maven Central
                      env:
                        MAVEN_TOKEN: ${{ secrets.SONATYPE_TOKEN }}
              """,
            source -> source.path(".github/workflows/publish.yml")
          )
        );
    }

    @Test
    void replaceSecretsWithVariableWhitespace() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSecrets("OSSRH_S01_USERNAME", "SONATYPE_USERNAME", null)),
          //language=yaml
          yaml(
            """
              name: Publish
              jobs:
                build:
                  env:
                    STANDARD: ${{ secrets.OSSRH_S01_USERNAME }}
                    NO_SPACES: ${{secrets.OSSRH_S01_USERNAME}}
                    EXTRA_SPACES: ${{   secrets.OSSRH_S01_USERNAME   }}
                    MIXED_SPACES: ${{  secrets.OSSRH_S01_USERNAME }}
              """,
            """
              name: Publish
              jobs:
                build:
                  env:
                    STANDARD: ${{ secrets.SONATYPE_USERNAME }}
                    NO_SPACES: ${{ secrets.SONATYPE_USERNAME }}
                    EXTRA_SPACES: ${{ secrets.SONATYPE_USERNAME }}
                    MIXED_SPACES: ${{ secrets.SONATYPE_USERNAME }}
              """,
            source -> source.path(".github/workflows/publish.yml")
          )
        );
    }

    @Test
    void replaceSecretKeyNames() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceSecretKeys("ossrh_username", "sonatype_username", null)),
          //language=yaml
          yaml(
            """
              name: Publish
              jobs:
                build:
                  secrets:
                    ossrh_username: ${{ secrets.OSSRH_S01_USERNAME }}
                    ossrh_token: ${{ secrets.OSSRH_S01_TOKEN }}
              """,
            """
              name: Publish
              jobs:
                build:
                  secrets:
                    sonatype_username: ${{ secrets.OSSRH_S01_USERNAME }}
                    ossrh_token: ${{ secrets.OSSRH_S01_TOKEN }}
              """,
            source -> source.path(".github/workflows/publish.yml")
          )
        );
    }

    @Test
    void compositeRecipeReplacesAllSecrets() {
        rewriteRun(
          spec -> spec.recipeFromYaml(/*language=yaml*/ """
            type: specs.openrewrite.org/v1beta/recipe
            name: org.openrewrite.github.ReplaceOssrhSecretsWithSonatype
            displayName: Replace OSSRH secrets with Sonatype secrets
            description: Replace deprecated OSSRH_S01 secrets with new Sonatype secrets in GitHub Actions workflows.
            recipeList:
              - org.openrewrite.github.ReplaceSecrets:
                  oldSecretName: OSSRH_S01_USERNAME
                  newSecretName: SONATYPE_USERNAME
              - org.openrewrite.github.ReplaceSecrets:
                  oldSecretName: OSSRH_S01_TOKEN
                  newSecretName: SONATYPE_TOKEN
              - org.openrewrite.github.ReplaceSecretKeys:
                  oldKeyName: ossrh_username
                  newKeyName: sonatype_username
              - org.openrewrite.github.ReplaceSecretKeys:
                  oldKeyName: ossrh_token
                  newKeyName: sonatype_token
            """, "org.openrewrite.github.ReplaceOssrhSecretsWithSonatype"),
          //language=yaml
          yaml(
            """
              name: Publish
              jobs:
                build:
                  secrets:
                    ossrh_username: ${{ secrets.OSSRH_S01_USERNAME }}
                    ossrh_token: ${{ secrets.OSSRH_S01_TOKEN }}
                  steps:
                    - name: Publish to Maven Central
                      env:
                        MAVEN_USERNAME: ${{ secrets.OSSRH_S01_USERNAME }}
                        MAVEN_TOKEN: ${{ secrets.OSSRH_S01_TOKEN }}
              """,
            """
              name: Publish
              jobs:
                build:
                  secrets:
                    sonatype_username: ${{ secrets.SONATYPE_USERNAME }}
                    sonatype_token: ${{ secrets.SONATYPE_TOKEN }}
                  steps:
                    - name: Publish to Maven Central
                      env:
                        MAVEN_USERNAME: ${{ secrets.SONATYPE_USERNAME }}
                        MAVEN_TOKEN: ${{ secrets.SONATYPE_TOKEN }}
              """,
            source -> source.path(".github/workflows/publish.yml")
          )
        );
    }
}
