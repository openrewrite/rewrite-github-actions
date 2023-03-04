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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import java.util.Random;

import static org.openrewrite.yaml.Assertions.yaml;

class AddCronTriggerTest implements RewriteTest {

    @ParameterizedTest
    @ValueSource(strings = {
      "0 18 * * *",
      "0 17 * 1 2"
    })
    void cronTrigger(String cron) {

        rewriteRun(
          spec -> spec.recipe(new AddCronTrigger(cron)),
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

    static class StaticThreadLocalRandom extends Random{
        @Override
        public int nextInt(int any) {
            return 1;
        }

    }

    @ParameterizedTest
    @CsvSource(value = {
      "@daily    |     1 1 * * *",
      "@weekly   |     1 1 * * 1",
      "@monthly  |     1 1 2 * 1",
      "@hourly   |     * 1 * * *",
      "@yearly   |     1 1 2 2 1",
      "@weekends |     1 1 * * 6,0"
    },delimiter = '|')
    void cronTriggerRandom(String cronExpression,String actualCronValue) {
        rewriteRun(
          spec -> spec.recipe(new AddCronTrigger(cronExpression, new StaticThreadLocalRandom())),
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
