/*
 * Copyright 2024 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class FindMissingTimeoutTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindMissingTimeout());
    }

    @DocumentExample
    @Test
    void findMissingTimeout() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v2
                    - name: Run a one-line script
                      run: echo Hello, world!
              """,
            """
              jobs:
                ~~(missing: $.jobs.*.timeout-minutes)~~>build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v2
                    - name: Run a one-line script
                      run: echo Hello, world!
              """,
            spec -> spec.path(".github/workflows/build.yml")
          )
        );
    }

    @Test
    void siblingJobHasTimeout() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v2
                    - name: Run a one-line script
                      run: echo Hello, world!
                package:
                  runs-on: ubuntu-latest
                  timeout-minutes: 10
                  steps:
                    - uses: actions/checkout@v2
                    - name: Run a one-line script
                      run: echo Hello, world!
              """,
            """
              jobs:
                ~~(missing: $.jobs.*.timeout-minutes)~~>build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v2
                    - name: Run a one-line script
                      run: echo Hello, world!
                package:
                  runs-on: ubuntu-latest
                  timeout-minutes: 10
                  steps:
                    - uses: actions/checkout@v2
                    - name: Run a one-line script
                      run: echo Hello, world!
              """,
            spec -> spec.path(".github/workflows/build.yml")
          )
        );
    }

    @Test
    void jobHasTimeout() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  timeout-minutes: 10
                  steps:
                    - uses: actions/checkout@v2
                    - name: Run a one-line script
                      run: echo Hello, world!
              """,
            spec -> spec.path(".github/workflows/build.yml")
          )
        );
    }

    @Test
    void stepHasTimeout() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v2
                      timeout-minutes: 10
                    - name: Run a one-line script
                      run: echo Hello, world!
              """,
            spec -> spec.path(".github/workflows/build.yml")
          )
        );
    }
}
