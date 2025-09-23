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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.yaml.ChangeValue;

import java.util.Arrays;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class ReplaceRunners extends Recipe {
    @Option(displayName = "Job Name",
            description = "The name of the job to update",
            example = "build")
    String jobName;

    @Option(displayName = "Runners",
            description = "The new list of runners to set",
            example = "ubuntu-latest"
    )
    List<String> runners;

    @Override
    public String getDisplayName() {
        return "Replace runners for a job";
    }

    @Override
    public String getDescription() {
        return "Replaces the runners of a given job.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"),
                new ChangeValue(String.format("$.jobs.%s.runs-on", jobName), Arrays.toString(runners.toArray()), null)
                        .getVisitor());
    }
}
