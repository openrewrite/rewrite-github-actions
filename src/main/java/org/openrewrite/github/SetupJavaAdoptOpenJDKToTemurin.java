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

import java.util.Arrays;
import java.util.Set;

import static java.util.Collections.singleton;

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
    public Set<String> getTags() {
        return singleton("security");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/workflows/*.{yml,yaml}"), new SetupJavaDistributionReplacerVisitor(Arrays.asList("adopt", "adopt-hotspot"), "temurin"));
    }

}
