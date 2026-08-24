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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class RemoveCronTriggerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveCronTrigger("0 11 * * *"));
    }

    @DocumentExample
    @Test
    void removeMatchingCronTrigger() {
        rewriteRun(
          //language=yml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
                schedule:
                  - cron: "0 18 * * *"
                  - cron: "0 11 * * *"
              """,
            """
              on:
                push:
                  branches:
                    - main
                schedule:
                  - cron: "0 18 * * *"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void removeScheduleWhenItBecomesEmpty() {
        rewriteRun(
          //language=yml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
                schedule:
                  - cron: 0 11 * * *
              """,
            """
              on:
                push:
                  branches:
                    - main
              """,
            spec -> spec.path(".github/workflows/ci.yaml")
          )
        );
    }

    @Test
    void leaveOtherCronTriggersUnchanged() {
        rewriteRun(
          //language=yml
          yaml(
            """
              on:
                schedule:
                  - cron: "0 18 * * *"
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void leaveNonWorkflowFilesUnchanged() {
        rewriteRun(
          //language=yml
          yaml(
            """
              on:
                schedule:
                  - cron: "0 11 * * *"
              """,
            spec -> spec.path("config.yml")
          )
        );
    }
}
