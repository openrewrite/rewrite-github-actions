/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.*;
import org.openrewrite.yaml.DeleteKey;

import java.time.Duration;

public class RemoveAllCronTriggers extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove all cron triggers";
    }

    @Override
    public String getDescription() {
        return "Removes all cron triggers from a workflow.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"),
                new DeleteKey("$.on.schedule", null).getVisitor());
    }
}
