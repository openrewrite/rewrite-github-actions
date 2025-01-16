/*
 * Copyright 2025 the original author or authors.
 *
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

import static org.openrewrite.yaml.Assertions.yaml;

class PreferSecretsInheritWorkflowTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreferSecretsInheritWorkflow());
    }

    @DocumentExample
    @Test
    void replaceIdenticalSecretsWithInherit() {
        rewriteRun(
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
