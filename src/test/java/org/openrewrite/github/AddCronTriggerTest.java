/*
 * Copyright 2025 the original author or authors.
 *
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.test.RewriteTest;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.openrewrite.yaml.Assertions.yaml;

class AddCronTriggerTest implements RewriteTest {

    @ParameterizedTest
    @ValueSource(strings = {
      "0 18 * * *",
      "0 17 * 1 2"
    })
    void cronTrigger(String cron) {
        rewriteRun(
          spec -> spec.recipe(new AddCronTrigger(cron, null)),
          //language=yml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
              """,
            """
              on:
                push:
                  branches:
                    - main
                schedule:
                  - cron: "%s"
              """.formatted(cron),
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "continuous_integration.yml",
      "*_integration.yml",
      "*.yml"
    })
    void makesChangesForMatchingWorkflow(String workflow) {
        rewriteRun(
          spec -> spec.recipe(new AddCronTrigger( "0 18 * * *", workflow)),
          //language=yml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
              """,
            """
              on:
                push:
                  branches:
                    - main
                schedule:
                  - cron: "0 18 * * *"
              """,
            spec -> spec.path(".github/workflows/continuous_integration.yml")
          )
        );
    }

    @Test
    void invalidFileMatcherShouldNotMakeChanges() {
        rewriteRun(
          spec -> spec.recipe(new AddCronTrigger("0 17 * 1 2", "someOtherFile.yml")),
          //language=yml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @ParameterizedTest
    @DisplayName("Missing or null defaults to .github/workflows/ci.yml")
    @NullSource
    @ValueSource(strings = {
      ""
    })
    void missingFileMatcher(String fileMatcher) {
        rewriteRun(
          spec -> spec.recipe(new AddCronTrigger("0 18 * * *", fileMatcher)),
          //language=yml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
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

    static class StaticThreadLocalRandom extends Random {
        @Override
        public int nextInt(int any) {
            return 1;
        }

    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
      @daily    |     1 1 * * *
      @weekly   |     1 1 * * tue
      @monthly  |     1 1 2 * *
      @hourly   |     * 1 * * *
      @yearly   |     1 1 2 feb *
      @weekdays |     1 1 * * 1-5
      @weekends |     1 1 * * sat,sun
      """)
    void cronTriggerRandom(String cronExpression, String actualCronValue) {
        rewriteRun(
          spec -> spec.recipe(new AddCronTrigger(cronExpression, "", new StaticThreadLocalRandom())),
          //language=yml
          yaml(
            """
              on:
                push:
                  branches:
                    - main
              """,
            """
              on:
                push:
                  branches:
                    - main
                schedule:
                  - cron: "%s"
              """.formatted(actualCronValue),
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }
}
