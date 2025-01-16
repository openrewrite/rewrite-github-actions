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
