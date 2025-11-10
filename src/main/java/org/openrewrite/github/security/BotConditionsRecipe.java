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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.github.IsGitHubActionsWorkflow;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class BotConditionsRecipe extends Recipe {

    // Known bot actor IDs that should be compared numerically, not as strings
    private static final Set<String> KNOWN_BOT_ACTOR_IDS = new HashSet<>(Arrays.asList(
            "29110",    // dependabot[bot]'s integration ID
            "49699333", // dependabot[bot]
            "27856297", // dependabot-preview[bot]
            "29139614"  // renovate[bot]
    ));

    // Patterns to detect spoofable actor name contexts
    private static final Pattern[] SPOOFABLE_ACTOR_NAME_PATTERNS = {
            Pattern.compile("github\\.actor\\s*==\\s*['\"][^'\"]*\\[bot\\]['\"]"),
            Pattern.compile("github\\.triggering_actor\\s*==\\s*['\"][^'\"]*\\[bot\\]['\"]"),
            Pattern.compile("github\\.event\\.pull_request\\.sender\\.login\\s*==\\s*['\"][^'\"]*\\[bot\\]['\"]"),
            Pattern.compile("github\\.actor\\s*==\\s*['\"]dependabot\\[bot\\]['\"]"),
            Pattern.compile("github\\.actor\\s*==\\s*['\"]renovate\\[bot\\]['\"]"),
            Pattern.compile("github\\.actor\\s*==\\s*['\"][^'\"]*bot[^'\"]*['\"]")
    };

    // Pattern to detect contains() checks which are unreliable
    private static final Pattern CONTAINS_BOT_PATTERN = Pattern.compile(
            "contains\\s*\\(\\s*github\\.[^,]+,\\s*['\"]bot['\"]\\s*\\)"
    );

    // Pattern to detect actor_id string comparisons
    private static final Pattern ACTOR_ID_STRING_PATTERN = Pattern.compile(
            "github\\.(actor_id|event\\.pull_request\\.sender\\.id)\\s*==\\s*['\"]\\d+['\"]"
    );

    @Override
    public String getDisplayName() {
        return "Find spoofable bot actor checks";
    }

    @Override
    public String getDescription() {
        return "Find workflow conditions that check for bot actors in ways that can be spoofed. " +
                "Bot actor names (like `dependabot[bot]`) can be easily spoofed by creating accounts with similar names. " +
                "Use `actor_id` with numeric comparison instead for secure bot validation. " +
                "Based on [zizmor's `bot-conditions` audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/bot_conditions.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new BotConditionsVisitor()
        );
    }

    private static class BotConditionsVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            if (isIfEntry(mappingEntry)) {
                return checkIfCondition(mappingEntry);
            }

            return mappingEntry;
        }

        private boolean isIfEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getKey() instanceof Yaml.Scalar)) {
                return false;
            }
            Yaml.Scalar key = (Yaml.Scalar) entry.getKey();
            return "if".equals(key.getValue());
        }

        private Yaml.Mapping.Entry checkIfCondition(Yaml.Mapping.Entry entry) {
            if (!(entry.getValue() instanceof Yaml.Scalar)) {
                return entry;
            }

            String condition = ((Yaml.Scalar) entry.getValue()).getValue();

            // Check for spoofable actor name patterns
            for (Pattern pattern : SPOOFABLE_ACTOR_NAME_PATTERNS) {
                if (pattern.matcher(condition).find()) {
                    return SearchResult.found(entry,
                            "Bot actor name check is spoofable. Consider using actor_id instead for more secure bot validation.");
                }
            }

            // Check for unreliable contains() checks
            if (CONTAINS_BOT_PATTERN.matcher(condition).find()) {
                return SearchResult.found(entry,
                        "Bot actor check using contains() is unreliable and spoofable. Use exact actor_id comparison instead.");
            }

            // Check for actor_id string comparisons
            if (ACTOR_ID_STRING_PATTERN.matcher(condition).find()) {
                // Check if it's a known bot actor ID
                for (String botId : KNOWN_BOT_ACTOR_IDS) {
                    if (condition.contains("'" + botId + "'") || condition.contains("\"" + botId + "\"")) {
                        return SearchResult.found(entry,
                                "Using string comparison for actor_id. Consider using numeric comparison for better reliability.");
                    }
                }
            }

            return entry;
        }
    }
}
