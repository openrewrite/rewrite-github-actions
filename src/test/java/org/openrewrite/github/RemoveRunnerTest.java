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
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class RemoveRunnerTest implements RewriteTest {

    @DocumentExample
    @Test
    void removeRunnerFromFlowSequence() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "MyRunner")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: [ubuntu-latest, MyRunner, gpu]
                other:
                  runs-on: [ubuntu-latest, MyRunner]
              """,
            """
              jobs:
                build:
                  runs-on: [ubuntu-latest, gpu]
                other:
                  runs-on: [ubuntu-latest, MyRunner]
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void removeFirstRunnerFromFlowSequence() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "self-hosted")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: [ self-hosted, linux, x64 ]
              """,
            """
              jobs:
                build:
                  runs-on: [ linux, x64 ]
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void removeLastRunnerFromFlowSequence() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "x64")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: [self-hosted, linux, x64]
              """,
            """
              jobs:
                build:
                  runs-on: [self-hosted, linux]
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void removeRunnerFromBlockSequence() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "self-hosted")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on:
                    - self-hosted
                    - linux
                    - x64
                  steps:
                    - run: echo hi
              """,
            """
              jobs:
                build:
                  runs-on:
                    - linux
                    - x64
                  steps:
                    - run: echo hi
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void dropTrailingCommentOfRemovedRunner() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "gpu")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on:
                    - self-hosted
                    - gpu # for CUDA tests
                    - x64 # architecture
              """,
            """
              jobs:
                build:
                  runs-on:
                    - self-hosted
                    - x64 # architecture
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void removeRunnerFromEveryJob() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("*", "gpu")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: [self-hosted, gpu]
                test:
                  runs-on:
                    - self-hosted
                    - gpu
                lint:
                  runs-on: ubuntu-latest
              """,
            """
              jobs:
                build:
                  runs-on: [self-hosted]
                test:
                  runs-on:
                    - self-hosted
                lint:
                  runs-on: ubuntu-latest
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void matchRunnerCaseInsensitively() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "GPU")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: [self-hosted, gpu]
              """,
            """
              jobs:
                build:
                  runs-on: [self-hosted]
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void keepOnlyRunnerOfScalar() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "ubuntu-latest")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void keepOnlyRunnerOfSequence() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "self-hosted")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: [self-hosted, self-hosted]
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void doNotChangeOtherJobsOrUnknownRunners() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "gpu")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: [self-hosted, linux]
                other:
                  runs-on: [self-hosted, gpu]
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void removeRunnerFromGroupLabels() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "gpu")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on:
                    group: ubuntu-runners
                    labels: [ubuntu-20.04-16core, gpu]
              """,
            """
              jobs:
                build:
                  runs-on:
                    group: ubuntu-runners
                    labels: [ubuntu-20.04-16core]
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void removeScalarLabelWhenGroupRemains() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "gpu")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on:
                    labels: gpu
                    group: ubuntu-runners
              """,
            """
              jobs:
                build:
                  runs-on:
                    group: ubuntu-runners
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void dropTrailingCommentOfRemovedLabels() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "gpu")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on:
                    group: ubuntu-runners
                    labels: gpu # for CUDA tests
                    custom: value
              """,
            """
              jobs:
                build:
                  runs-on:
                    group: ubuntu-runners
                    custom: value
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void removeEmptiedLabelsWhenGroupRemains() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "gpu")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on:
                    group: ubuntu-runners
                    labels: [gpu]
              """,
            """
              jobs:
                build:
                  runs-on:
                    group: ubuntu-runners
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void keepSequenceLabelsWithoutGroup() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "gpu")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on:
                    labels: [gpu]
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void keepScalarLabelWithoutGroup() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "gpu")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on:
                    labels: gpu
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void ignoreNonWorkflowFiles() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRunner("build", "gpu")),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: [self-hosted, gpu]
              """,
            spec -> spec.path("config/ci.yml")
          )
        );
    }
}
