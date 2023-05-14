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

import org.openrewrite.*;
import org.openrewrite.yaml.YamlVisitor;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.Arrays;

public class SetupJavaAdoptOpenJDKToTemurin extends Recipe {


    @Override
    public String getDisplayName() {
        return "Use `actions/setup-java` `temurin` distribution";
    }

    @Override
    public String getDescription() {
        return "Adopt OpenJDK got moved to Eclipse Temurin and won't be updated anymore. " +
                "It is highly recommended to migrate workflows from adopt to temurin to keep receiving software and security updates. " +
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
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new HasSourcePath<>(".github/workflows/*.yml");
    }

    @Override
    protected YamlVisitor<ExecutionContext> getVisitor() {
        return new SetupJavaDistributionReplacerVisitor(Arrays.asList("adopt", "adopt-hotspot"), "temurin");
    }

}
