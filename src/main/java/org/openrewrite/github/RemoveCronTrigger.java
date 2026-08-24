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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.time.Duration;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveCronTrigger extends Recipe {

    @Option(displayName = "Cron expression",
            description = "The cron expression of the schedule to remove.",
            example = "0 11 * * *")
    String cron;

    String displayName = "Remove cron workflow trigger";

    String description = "Removes a specific cron trigger from a GitHub Actions workflow.";

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsWorkflow(), new YamlIsoVisitor<ExecutionContext>() {
            private final JsonPathMatcher scheduleMatcher = new JsonPathMatcher("$.on.schedule");

            @Override
            public Yaml.Mapping.@Nullable Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                if (!scheduleMatcher.matches(getCursor()) || !(e.getValue() instanceof Yaml.Sequence)) {
                    return e;
                }

                Yaml.Sequence schedule = (Yaml.Sequence) e.getValue();
                List<Yaml.Sequence.Entry> remaining = ListUtils.map(schedule.getEntries(), scheduleEntry ->
                        matchesCron(scheduleEntry, cron) ? null : scheduleEntry);
                return remaining.isEmpty() ? null : e.withValue(schedule.withEntries(remaining));
            }
        });
    }

    private static boolean matchesCron(Yaml.Sequence.Entry scheduleEntry, String cron) {
        if (!(scheduleEntry.getBlock() instanceof Yaml.Mapping)) {
            return false;
        }
        Yaml.Mapping schedule = (Yaml.Mapping) scheduleEntry.getBlock();
        return schedule.getEntries().stream().anyMatch(entry ->
                "cron".equals(entry.getKey().getValue()) &&
                entry.getValue() instanceof Yaml.Scalar &&
                cron.equals(((Yaml.Scalar) entry.getValue()).getValue()));
    }
}
