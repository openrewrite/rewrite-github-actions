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

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.yaml.ChangeValue;
import org.openrewrite.yaml.DeleteKey;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.search.FindKey;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Objects;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class UpgradeSlackNotificationVersion2 extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade `slackapi/slack-github-action`";
    }

    @Override
    public String getDescription() {
        return "Update the Slack GitHub Action to use version 2.0.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"), new UpgradeSlackNotificationActionVisitor());
    }

    @AllArgsConstructor
    private static class UpgradeSlackNotificationActionVisitor extends YamlVisitor<ExecutionContext> {
        private static final String jsonPath = "$..steps[?(@.uses =~ 'slackapi/slack-github-action@v1.*')]";

        @Override
        public Yaml visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
            Yaml.Documents d = documents;
            Set<Yaml> slackAction = FindKey.find(documents, jsonPath);

            if (slackAction.isEmpty()) {
                return documents;
            }

            Yaml slackActionFragment = slackAction.iterator().next();
            Set<Yaml> token = FindKey.find(slackActionFragment, "$.env.SLACK_BOT_TOKEN");
            Set<Yaml> channel = FindKey.find(slackActionFragment, "$.with.channel-id");
            Set<Yaml> message = FindKey.find(slackActionFragment, "$.with.slack-message");

            if (token.isEmpty() || channel.isEmpty() || message.isEmpty()) {
                return documents;
            }

            String slackToken = ((Yaml.Scalar) ((Yaml.Mapping.Entry) token.iterator().next()).getValue()).getValue();
            String channelName = ((Yaml.Scalar) ((Yaml.Mapping.Entry) channel.iterator().next()).getValue()).getValue();
            String messageText = ((Yaml.Scalar) ((Yaml.Mapping.Entry) message.iterator().next()).getValue()).getValue();

            d = (Yaml.Documents) new MergeYaml(jsonPath,
                    "with:\n" +
                            "  method: chat.postMessage\n" +
                            "  token: " + slackToken + "\n" +
                            "  payload: |\n" +
                            "            channel: \"" + channelName + "\"\n" +
                            "            text: \"" + messageText + "\"\n",
                    false, null, null)
                    .getVisitor().visitNonNull(d, ctx);

            d = (Yaml.Documents) new DeleteKey(jsonPath + ".with.channel-id", null)
                    .getVisitor().visitNonNull(d, ctx);
            d = (Yaml.Documents) new DeleteKey(jsonPath + ".with.slack-message", null)
                    .getVisitor().visitNonNull(d, ctx);
            d = (Yaml.Documents) new DeleteKey(jsonPath + ".env.SLACK_BOT_TOKEN", null)
                    .getVisitor().visitNonNull(d, ctx);

            d = (Yaml.Documents) new ChangeValue(jsonPath + ".uses", "slackapi/slack-github-action@v2.0.0", null)
                    .getVisitor().visitNonNull(d, ctx);

            return autoFormat(d, ctx, Objects.requireNonNull(getCursor().getParent()));
        }
    }
}
