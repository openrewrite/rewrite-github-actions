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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

public class SetupJavaAdoptOpenj9ToSemeru extends Recipe {

    @Getter
    final String displayName = "Use `actions/setup-java` IBM `semeru` distribution";

    @Getter
    final String description = "Adopt OpenJDK got moved to Eclipse Temurin and won't be updated anymore. " +
                "It is highly recommended to migrate workflows from adopt-openj9 to IBM semeru to keep receiving software and security updates. " +
                "See more details in the [Good-bye AdoptOpenJDK post](https://blog.adoptopenjdk.net/2021/08/goodbye-adoptopenjdk-hello-adoptium/).";

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Getter
    final Set<String> tags = singleton( "security" );

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsWorkflow(),
                new SetupJavaDistributionReplacerVisitor(singletonList("adopt-openj9"), "semeru"));
    }

}
