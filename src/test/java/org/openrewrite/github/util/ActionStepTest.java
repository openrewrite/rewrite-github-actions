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
package org.openrewrite.github.util;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.yaml.Assertions.yaml;

class ActionStepTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(RewriteTest.toRecipe(() -> new YamlIsoVisitor<>() {
            // No-op visitor - we're testing trait matching, not transformation
        }));
    }

    @DocumentExample
    @Test
    void findsActionSteps() {
        rewriteRun(
                yaml(
                        """
                        name: Test Workflow
                        on: push
                        jobs:
                          build:
                            runs-on: ubuntu-latest
                            steps:
                              - uses: actions/checkout@v3
                              - uses: actions/setup-java@v2
                                with:
                                  distribution: 'temurin'
                              - run: echo "Not an action step"
                        """,
                        spec -> spec.afterRecipe(results -> {
                            ActionStep.Matcher matcher = new ActionStep.Matcher();
                            List<ActionStep> steps = matcher.lower(results)
                                    .collect(Collectors.toList());

                            assertThat(steps).hasSize(2);
                            assertThat(steps.get(0).getActionName()).isEqualTo("actions/checkout");
                            assertThat(steps.get(0).getActionVersion()).isEqualTo("v3");
                            assertThat(steps.get(1).getActionName()).isEqualTo("actions/setup-java");
                        })
                )
        );
    }

    @Test
    void matcherWithFilterFindsSpecificAction() {
        rewriteRun(
                yaml(
                        """
                        jobs:
                          test:
                            steps:
                              - uses: actions/checkout@v3
                              - uses: actions/setup-java@v2
                              - uses: gradle/gradle-build-action@v2
                        """,
                        spec -> spec.afterRecipe(results -> {
                            ActionStep.Matcher matcher = new ActionStep.Matcher()
                                    .withRequiredAction("actions/setup-java");

                            List<ActionStep> steps = matcher.lower(results)
                                    .collect(Collectors.toList());

                            assertThat(steps).hasSize(1);
                            assertThat(steps.get(0).getActionName()).isEqualTo("actions/setup-java");
                        })
                )
        );
    }

    @Test
    void doesNotMatchRunSteps() {
        rewriteRun(
                yaml(
                        """
                        jobs:
                          test:
                            steps:
                              - run: echo "hello"
                              - name: Build
                                run: ./gradlew build
                        """,
                        spec -> spec.afterRecipe(results -> {
                            ActionStep.Matcher matcher = new ActionStep.Matcher();
                            List<ActionStep> steps = matcher.lower(results)
                                    .collect(Collectors.toList());

                            assertThat(steps).isEmpty();
                        })
                )
        );
    }

    @Test
    void extractsActionNameAndVersion() {
        rewriteRun(
                yaml(
                        """
                        jobs:
                          build:
                            steps:
                              - uses: actions/checkout@v3
                              - uses: octocat/hello-world-action@main
                              - uses: actions/setup-java@a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0
                        """,
                        spec -> spec.afterRecipe(results -> {
                            ActionStep.Matcher matcher = new ActionStep.Matcher();
                            List<ActionStep> steps = matcher.lower(results)
                                    .collect(Collectors.toList());

                            // Test semantic version
                            assertThat(steps.get(0).getActionName()).isEqualTo("actions/checkout");
                            assertThat(steps.get(0).getActionVersion()).isEqualTo("v3");
                            assertThat(steps.get(0).getActionOwner()).isEqualTo("actions");
                            assertThat(steps.get(0).isVersionPinned()).isFalse();

                            // Test branch reference
                            assertThat(steps.get(1).getActionName()).isEqualTo("octocat/hello-world-action");
                            assertThat(steps.get(1).getActionVersion()).isEqualTo("main");
                            assertThat(steps.get(1).isVersionPinned()).isFalse();

                            // Test SHA pinning
                            assertThat(steps.get(2).getActionName()).isEqualTo("actions/setup-java");
                            assertThat(steps.get(2).isVersionPinned()).isTrue();
                        })
                )
        );
    }

    @Test
    void matchesActionPatterns() {
        rewriteRun(
                yaml(
                        """
                        jobs:
                          build:
                            steps:
                              - uses: actions/setup-java@v2
                        """,
                        spec -> spec.afterRecipe(results -> {
                            ActionStep.Matcher matcher = new ActionStep.Matcher();
                            ActionStep step = matcher.lower(results)
                                    .findFirst()
                                    .orElseThrow();

                            assertThat(step.matchesAction("actions/setup-java")).isTrue();
                            assertThat(step.matchesAction("actions/setup-java@*")).isTrue();
                            assertThat(step.matchesAction("actions/setup-node")).isFalse();
                        })
                )
        );
    }

    @Test
    void handlesActionWithoutVersion() {
        rewriteRun(
                yaml(
                        """
                        jobs:
                          build:
                            steps:
                              - uses: actions/checkout
                        """,
                        spec -> spec.afterRecipe(results -> {
                            ActionStep.Matcher matcher = new ActionStep.Matcher();
                            List<ActionStep> steps = matcher.lower(results)
                                    .collect(Collectors.toList());

                            assertThat(steps).hasSize(1);
                            assertThat(steps.get(0).getActionName()).isEqualTo("actions/checkout");
                            assertThat(steps.get(0).getActionVersion()).isNull();
                            assertThat(steps.get(0).isVersionPinned()).isFalse();
                        })
                )
        );
    }

    @Test
    void doesNotMatchUsesOutsideSteps() {
        rewriteRun(
                yaml(
                        """
                        jobs:
                          call-workflow:
                            uses: ./.github/workflows/reusable.yml
                          build:
                            steps:
                              - uses: actions/checkout@v3
                        """,
                        spec -> spec.afterRecipe(results -> {
                            ActionStep.Matcher matcher = new ActionStep.Matcher();
                            List<ActionStep> steps = matcher.lower(results)
                                    .collect(Collectors.toList());

                            // Should only find the step-level uses, not the job-level uses
                            assertThat(steps).hasSize(1);
                            assertThat(steps.get(0).getActionName()).isEqualTo("actions/checkout");
                        })
                )
        );
    }

    @Test
    void handlesMultipleJobsAndSteps() {
        rewriteRun(
                yaml(
                        """
                        jobs:
                          build:
                            steps:
                              - uses: actions/checkout@v3
                              - run: echo "build"
                          test:
                            steps:
                              - uses: actions/checkout@v3
                              - uses: actions/setup-java@v2
                          deploy:
                            steps:
                              - uses: actions/checkout@v3
                        """,
                        spec -> spec.afterRecipe(results -> {
                            ActionStep.Matcher matcher = new ActionStep.Matcher();
                            List<ActionStep> steps = matcher.lower(results)
                                    .collect(Collectors.toList());

                            assertThat(steps).hasSize(4);
                            // Three checkouts and one setup-java
                            long checkouts = steps.stream()
                                    .filter(s -> "actions/checkout".equals(s.getActionName()))
                                    .count();
                            assertThat(checkouts).isEqualTo(3);
                        })
                )
        );
    }

    @Test
    void handlesLocalActions() {
        rewriteRun(
                yaml(
                        """
                        jobs:
                          build:
                            steps:
                              - uses: ./local-action
                              - uses: actions/checkout@v3
                        """,
                        spec -> spec.afterRecipe(results -> {
                            ActionStep.Matcher matcher = new ActionStep.Matcher();
                            List<ActionStep> steps = matcher.lower(results)
                                    .collect(Collectors.toList());

                            // Both should match - local actions are still action steps
                            assertThat(steps).hasSize(2);
                            assertThat(steps.get(0).getActionRef()).isEqualTo("./local-action");
                            assertThat(steps.get(0).getActionName()).isEqualTo("./local-action");
                            assertThat(steps.get(0).getActionOwner()).isNull(); // No owner for local actions
                        })
                )
        );
    }

    @Test
    void handlesDockerActions() {
        rewriteRun(
                yaml(
                        """
                        jobs:
                          build:
                            steps:
                              - uses: docker://alpine:3.8
                              - uses: actions/checkout@v3
                        """,
                        spec -> spec.afterRecipe(results -> {
                            ActionStep.Matcher matcher = new ActionStep.Matcher();
                            List<ActionStep> steps = matcher.lower(results)
                                    .collect(Collectors.toList());

                            assertThat(steps).hasSize(2);
                            assertThat(steps.get(0).getActionRef()).isEqualTo("docker://alpine:3.8");
                        })
                )
        );
    }
}
