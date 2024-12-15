/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class SetupJavaAdoptOpenj9ToSemeru extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use `actions/setup-java` IBM `semeru` distribution";
    }

    @Override
    public String getDescription() {
        return "Adopt OpenJDK got moved to Eclipse Temurin and won't be updated anymore. " +
                "It is highly recommended to migrate workflows from adopt-openj9 to IBM semeru to keep receiving software and security updates. " +
                "See more details in the [Good-bye AdoptOpenJDK post](https://blog.adoptopenjdk.net/2021/08/goodbye-adoptopenjdk-hello-adoptium/).";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("security");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"),
                new SetupJavaDistributionReplacerVisitor(Collections.singletonList("adopt-openj9"), "semeru"));
    }

}
