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

class UpgradeSlackNotificationVersion2Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSlackNotificationVersion2());
    }

    @DocumentExample
    @Test
    void updatesVersion2() {
        rewriteRun(
          // language=yaml
          yaml(
            """
              jobs:
                build:
                  steps:
                    - name: Send notification on error
                      if: failure() && inputs.send-notification
                      uses: slackapi/slack-github-action@v1.27.0
                      with:
                        channel-id: "##foo-alerts"
                        slack-message: ":boom: Unable run dependency check on: <${{ steps.get_failed_check_link.outputs.failed-check-link }}|${{ inputs.organization }}/${{ inputs.repository }}>"
                      env:
                        SLACK_BOT_TOKEN: ${{ secrets.SLACK_MORTY_BOT_TOKEN }}
              """,
            """
              jobs:
                build:
                  steps:
                    - name: Send notification on error
                      if: failure() && inputs.send-notification
                      uses: slackapi/slack-github-action@v2.0.0
                      with:
                        method: chat.postMessage
                        token: ${{ secrets.SLACK_MORTY_BOT_TOKEN }}
                        payload: |
                          channel: "##foo-alerts"
                          text: ":boom: Unable run dependency check on: <${{ steps.get_failed_check_link.outputs.failed-check-link }}|${{ inputs.organization }}/${{ inputs.repository }}>"
              """,
            spec -> spec.path(".github/workflows/ci.yml")));
    }
}
