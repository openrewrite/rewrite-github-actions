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

import static org.openrewrite.yaml.Assertions.yaml;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

class PreferSecretsInheritWorkflowTest implements RewriteTest {

    @DocumentExample
    @Test
    void replaceIdenticalSecretsWithInherit() {
        rewriteRun(
          spec -> spec.recipe(new PreferSecretsInheritWorkflow()),
          //language=yaml
          yaml(
            """
                jobs:
                  call-workflow-passing-data:
                    uses: octo-org/example-repo/.github/workflows/reusable-workflow.yml@main
                    with:
                      config-path: .github/labeler.yml
                    secrets:
                      envPAT: ${{ secrets.envPAT }}
              """,
            """
                jobs:
                  call-workflow-passing-data:
                    uses: octo-org/example-repo/.github/workflows/reusable-workflow.yml@main
                    with:
                      config-path: .github/labeler.yml
                    secrets: inherit
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void leaveModifiedSecrets() {
        rewriteRun(
          spec -> spec.recipe(new PreferSecretsInheritWorkflow()),
          //language=yaml
          yaml(
            """
                jobs:
                  call-workflow-passing-data:
                    uses: octo-org/example-repo/.github/workflows/reusable-workflow.yml@main
                    with:
                      config-path: .github/labeler.yml
                    secrets:
                      envPAT: ${{ secrets.envPAT }}
                      some_secret: ${{ secrets.SOME_SECRET }}
                """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void transformFromOtherWorkflowFile() {
        rewriteRun(
          spec -> spec.recipe(new PreferSecretsInheritWorkflow()),
          //language=yaml
          yaml(
            """
                    jobs:
                      call-workflow-passing-data:
                        uses: octo-org/example-repo/.github/workflows/reusable-workflow.yml@main
                        with:
                          config-path: .github/labeler.yml
                        secrets:
                          envPAT: ${{ secrets.envPAT }}
                  """,
            """
                jobs:
                  call-workflow-passing-data:
                    uses: octo-org/example-repo/.github/workflows/reusable-workflow.yml@main
                    with:
                      config-path: .github/labeler.yml
                    secrets: inherit
              """,
            spec -> spec.path(".github/workflows/some-workflow.yml")
          )
        );
    }

    @Test
    void handleAdditionalSpacesInSecretsReference() {
        rewriteRun(
          spec -> spec.recipe(new PreferSecretsInheritWorkflow()),
          //language=yaml
          yaml(
            """
                jobs:
                  call-workflow-passing-data:
                    uses: octo-org/example-repo/.github/workflows/reusable-workflow.yml@main
                    with:
                      config-path: .github/labeler.yml
                    secrets:
                      envPAT: ${{        secrets.envPAT}}
              """,
            """
                jobs:
                  call-workflow-passing-data:
                    uses: octo-org/example-repo/.github/workflows/reusable-workflow.yml@main
                    with:
                      config-path: .github/labeler.yml
                    secrets: inherit
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
