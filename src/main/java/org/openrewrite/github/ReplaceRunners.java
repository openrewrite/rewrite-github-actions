/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.*;
import org.openrewrite.yaml.ChangeValue;

import java.util.Arrays;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
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
