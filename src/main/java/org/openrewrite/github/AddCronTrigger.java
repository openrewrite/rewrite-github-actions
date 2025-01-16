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
package org.openrewrite.github;

import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.yaml.MergeYaml;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

@EqualsAndHashCode(callSuper = false)
@Getter
public class AddCronTrigger extends Recipe {
    @Option(displayName = "Cron expression",
            description = "Using the [POSIX cron syntax](https://pubs.opengroup.org/onlinepubs/9699919799/utilities/crontab.html#tag_20_25_07) or the non standard options" +
                    " @hourly @daily @weekly @weekdays @weekends @monthly @yearly.",
            example = "@daily")
    private final String cron;

    @Option(displayName = "Workflow files to match",
            description = "Matches one or more workflows to update. Defaults to `*.yml`",
            required = false,
            example = "build.yml")
    @Nullable
    private final String workflowFileMatcher;

    @VisibleForTesting
    transient Random random;

    @VisibleForTesting
    AddCronTrigger(String cron, @Nullable String workflowFileMatcher, Random random) {
        this.random = random;
        this.cron = cron;
        this.workflowFileMatcher = workflowFileMatcher;
    }

    public AddCronTrigger(String cron, @Nullable String fileMatcher) {
        this(cron, fileMatcher, ThreadLocalRandom.current());
    }

    private String parseExpression(String cron) {

        RandomCronExpression randomCronExpression = new RandomCronExpression(random);

        switch (cron) {
            case "@hourly":
                return randomCronExpression.hourlyCron();
            case "@daily":
                return randomCronExpression.dailyCron();
            case "@weekly":
                return randomCronExpression.weeklyCron();
            case "@weekdays":
                return randomCronExpression.weekdays();
            case "@weekends":
                return randomCronExpression.weekends();
            case "@monthly":
                return randomCronExpression.monthlyCron();
            case "@yearly":
                return randomCronExpression.yearlyCron();
            default:
                return cron;
        }
    }

    @Override
    public String getDisplayName() {
        return "Add cron workflow trigger";
    }

    @Override
    public String getDescription() {
        return "The `schedule` [event](https://docs.github.com/en/actions/reference/events-that-trigger-workflows#scheduled-events) allows you to trigger a workflow at a scheduled time.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String expression = parseExpression(cron);
        String path = StringUtils.isBlank(workflowFileMatcher) ? ".github/workflows/*.yml" : ".github/workflows/" + workflowFileMatcher;
        return Preconditions.check(new FindSourceFiles(path), new MergeYaml(
                "$.on",
                String.format(
                        "schedule:%n" +
                        "  - cron: \"%s\"", expression),
                true,
                null, null).getVisitor());
    }

    static class RandomCronExpression {
        private final Random random;

        public RandomCronExpression(Random random) {
            this.random = random;
        }

        public int random(int min, int max) {
            return random.nextInt(max + 1 - min) + min;
        }

        public int minute() {
            return random(0, 59);
        }

        public int hour() {
            return random(0, 23);
        }

        public String dayOfWeek() {
            final List<String> daysOfWeek = Arrays.asList("mon", "tue", "wed", "thu", "fri", "sat", "sun");
            return daysOfWeek.get(random.nextInt(daysOfWeek.size()));
        }

        public String dailyCron() {
            return String.format("%d %d * * *", minute(), hour());
        }

        public String weeklyCron() {
            return String.format("%d %d * * %s", minute(), hour(), dayOfWeek());
        }

        public String weekdays() {
            return String.format("%d %d * * 1-5", minute(), hour());
        }

        public String weekends() {
            return String.format("%d %d * * sat,sun", minute(), hour());
        }

        public String monthlyCron() {
            return String.format("%d %d %d * *", minute(), hour(), dayOfTheMonth());
        }

        public int dayOfTheMonth() {
            return random(1, 28);
        }

        public String month() {
            final List<String> months = Arrays.asList("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");
            return months.get(random.nextInt(months.size()));
        }

        public String hourlyCron() {
            return String.format("* %s * * *", hour());
        }

        public String yearlyCron() {
            return String.format("%d %d %d %s *", minute(), hour(), dayOfTheMonth(), month());
        }
    }

}
