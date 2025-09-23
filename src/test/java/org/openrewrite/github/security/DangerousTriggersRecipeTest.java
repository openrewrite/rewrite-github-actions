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
package org.openrewrite.github.security;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class DangerousTriggersRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DangerousTriggersRecipe());
    }

    @DocumentExample
    @Test
    void shouldDetectPullRequestTarget() {
        rewriteRun(
          yaml(
            """
              on: pull_request_target
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "test"
              """,
            """
              ~~(The 'pull_request_target' trigger is almost always used insecurely. It runs with write permissions in the context of the target repository, potentially allowing code injection from pull requests. Consider using 'pull_request' instead, or implement proper isolation.)~~>on: pull_request_target
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "test"
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldDetectWorkflowRun() {
        rewriteRun(
          yaml(
            """
              on: workflow_run
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "test"
              """,
            """
              ~~(The 'workflow_run' trigger is almost always used insecurely. It can trigger workflows with sensitive permissions based on external events. Consider using more specific triggers with explicit safety checks.)~~>on: workflow_run
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "test"
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldDetectDangerousTriggersInArray() {
        rewriteRun(
          yaml(
            """
              on: [push, pull_request_target, pull_request]
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "test"
              """,
            """
              ~~(The 'pull_request_target' trigger is almost always used insecurely. It runs with write permissions in the context of the target repository, potentially allowing code injection from pull requests. Consider using 'pull_request' instead, or implement proper isolation.)~~>on: [push, pull_request_target, pull_request]
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "test"
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldDetectDangerousTriggersInObject() {
        rewriteRun(
          yaml(
            """
              on:
                push:
                  branches: [main]
                pull_request_target:
                  types: [opened, synchronize]
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "test"
              """,
            """
              ~~(The 'pull_request_target' trigger is almost always used insecurely. It runs with write permissions in the context of the target repository, potentially allowing code injection from pull requests. Consider using 'pull_request' instead, or implement proper isolation.)~~>on:
                push:
                  branches: [main]
                pull_request_target:
                  types: [opened, synchronize]
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "test"
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldDetectMultipleDangerousTriggers() {
        rewriteRun(
          yaml(
            """
              on:
                pull_request_target:
                  types: [opened]
                workflow_run:
                  workflows: [build]
                push:
                  branches: [main]
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "test"
              """,
            """
              ~~(The 'pull_request_target' trigger is almost always used insecurely. It runs with write permissions in the context of the target repository, potentially allowing code injection from pull requests. Consider using 'pull_request' instead, or implement proper isolation.)~~>on:
                pull_request_target:
                  types: [opened]
                workflow_run:
                  workflows: [build]
                push:
                  branches: [main]
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "test"
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagSafeTriggers() {
        rewriteRun(
          yaml(
            """
              on:
                push:
                  branches: [main]
                pull_request:
                  branches: [main]
                release:
                  types: [published]
                schedule:
                  - cron: '0 0 * * *'
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "test"
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldNotFlagTriggersInJobContext() {
        rewriteRun(
          yaml(
            """
              on: push
              jobs:
                test:
                  runs-on: ubuntu-latest
                  steps:
                    - run: echo "This should not trigger on pull_request_target"
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }

    @Test
    void shouldHandleComplexWorkflowStructure() {
        rewriteRun(
          yaml(
            """
              name: Complex Workflow
              on:
                workflow_run:
                  workflows: ["CI"]
                  types: [completed]
                  branches: [main]
              permissions:
                contents: read
              env:
                NODE_VERSION: '18'
              jobs:
                test:
                  runs-on: ubuntu-latest
                  if: github.event.workflow_run.conclusion == 'success'
                  steps:
                    - uses: actions/checkout@v4
                    - run: npm test
              """,
            """
              name: Complex Workflow
              ~~(The 'workflow_run' trigger is almost always used insecurely. It can trigger workflows with sensitive permissions based on external events. Consider using more specific triggers with explicit safety checks.)~~>on:
                workflow_run:
                  workflows: ["CI"]
                  types: [completed]
                  branches: [main]
              permissions:
                contents: read
              env:
                NODE_VERSION: '18'
              jobs:
                test:
                  runs-on: ubuntu-latest
                  if: github.event.workflow_run.conclusion == 'success'
                  steps:
                    - uses: actions/checkout@v4
                    - run: npm test
              """,
            sourceSpecs -> sourceSpecs.path(".github/workflows/test.yml")
          )
        );
    }
}
