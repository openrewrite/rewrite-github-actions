/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class ActionsSetupJavaAdoptOpenJDKToTemurin extends Recipe {
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
        return new ActionsSetupJavaAdoptOpenJDKToTemurinVisitor();
    }

    private static class ActionsSetupJavaAdoptOpenJDKToTemurinVisitor extends YamlIsoVisitor<ExecutionContext> {
        private static final JsonPathMatcher inSteps = new JsonPathMatcher("..steps");
        private static final JsonPathMatcher distribution = new JsonPathMatcher("..[?(@.uses =~ 'actions/setup-java@v[23].*')].with.distribution");

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Cursor inSequence = getCursor().dropParentUntil(is -> is instanceof Yaml.Sequence.Entry || is instanceof Yaml.Document);
            if (inSequence.getValue() instanceof Yaml.Sequence.Entry) {
                Cursor inMappingEntry = inSequence.dropParentUntil(is -> is instanceof Yaml.Mapping.Entry || is instanceof Yaml.Document);
                if (inMappingEntry.getValue() instanceof Yaml.Mapping.Entry && inSteps.matches(inMappingEntry)) {
                    if (distribution.matches(getCursor()) && ((Yaml.Scalar) entry.getValue()).getValue().contains("adopt")) {
                        return super.visitMappingEntry(entry.withValue(((Yaml.Scalar) entry.getValue()).withValue("temurin")), ctx);
                    }
                }
            }
//                    if (distribution.matches(getCursor()) && ((Yaml.Scalar) entry.getValue()).getValue().contains("adopt")) {
//                        return super.visitMappingEntry(entry.withValue(((Yaml.Scalar) entry.getValue()).withValue("temurin")), ctx);
//                    }
            return super.visitMappingEntry(entry, ctx);
        }
    }
}
