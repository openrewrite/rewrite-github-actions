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

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.openrewrite.yaml.Assertions.yaml;

class UpgradeOfficialGitHubActionsTest implements RewriteTest {

    private static final Map<String, String> PROPS = loadProperties();
    private static final OfficialActionVersions VERSIONS = OfficialActionVersions.fromProperties(PROPS);

    private static final String LATEST_MAJOR = VERSIONS.upgrade("actions/checkout", "v1");
    private static final String LATEST_FULL = VERSIONS.upgrade("actions/checkout", "v1.0.0");
    private static final String OLD_SHA = PROPS.get("actions/checkout@v1.0.0");
    private static final String LATEST_MAJOR_SHA = PROPS.get("actions/checkout@" + LATEST_MAJOR);

    private static Map<String, String> loadProperties() {
        Properties props = new Properties();
        try (InputStream is = UpgradeOfficialGitHubActionsTest.class
                .getResourceAsStream("/META-INF/rewrite/known-action-shas.properties")) {
            props.load(is);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            map.put(key, props.getProperty(key));
        }
        return map;
    }

    @DocumentExample
    @Test
    void upgradesOfficialActionsPreservingPrecisionAndLeavingOthersUntouched() {
        rewriteRun(
          spec -> spec.recipe(new UpgradeOfficialGitHubActions()),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v1
                    - uses: actions/checkout@v1.0.0
                    - uses: actions/checkout@main
                    - uses: codecov/codecov-action@v1
              """.formatted(),
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@%s
                    - uses: actions/checkout@%s
                    - uses: actions/checkout@main
                    - uses: codecov/codecov-action@v1
              """.formatted(LATEST_MAJOR, LATEST_FULL),
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void upgradesCommitShaToLatestMajorSha() {
        rewriteRun(
          spec -> spec.recipe(new UpgradeOfficialGitHubActions()),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@%s
              """.formatted(OLD_SHA),
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@%s
              """.formatted(LATEST_MAJOR_SHA),
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void leavesUnknownAndAlreadyLatestActionsUntouched() {
        rewriteRun(
          spec -> spec.recipe(new UpgradeOfficialGitHubActions()),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/not-a-real-action@v1
                    - uses: actions/checkout@%s
                    - uses: ./.github/actions/local
              """.formatted(LATEST_MAJOR),
            source -> source.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void onlyRunsOnWorkflowFiles() {
        rewriteRun(
          spec -> spec.recipe(new UpgradeOfficialGitHubActions()),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - uses: actions/checkout@v1
              """,
            source -> source.path("not-a-workflow.yml")
          )
        );
    }
}
