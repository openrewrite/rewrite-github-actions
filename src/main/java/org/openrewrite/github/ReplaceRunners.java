/*
 * Copyright 2023 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.yaml.ChangeValue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Getter
public class ReplaceRunners extends Recipe {
    @Option(displayName = "Job Name",
            description = "The name of the job to update",
            example = "build")
    private final String jobName;

    @Option(displayName = "Runners",
            description = "The new list of runners to set",
            example = "ubuntu-latest"
    )
    private final List<String> runners;

    public ReplaceRunners(@NonNull String jobName, @NonNull List<String> runners) {
        this.jobName = jobName;
        this.runners = runners;
        doNext(new ChangeValue(
                String.format("$.jobs.%s.runs-on",jobName),
                Arrays.toString(runners.toArray()),
                ".github/workflows/*.yml")
        );
    }

    @Override
    public String getDisplayName() {
        return "Replace runners for a job";
    }

    @Override
    public String getDescription() {
        return "Replaces the runners of a given job.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }
}
