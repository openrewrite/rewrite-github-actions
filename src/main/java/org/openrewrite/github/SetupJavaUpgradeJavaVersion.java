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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

@Value
@EqualsAndHashCode(callSuper = true)
public class SetupJavaUpgradeJavaVersion extends Recipe {

    @Option(displayName = "Minimum major Java version (defaults to 17)",
            example = "17",
            required = false)
    @Nullable
    Integer minimumJavaMajorVersion;

    @Override
    public String getDisplayName() {
        return "Upgrade `actions/setup-java` `java-version`";
    }

    @Override
    public String getDescription() {
        return "Update the Java version used by `actions/setup-java` if it is below the expected version number.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new HasSourcePath<>(".github/workflows/*.yml");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UpgradeJavaVersionVisitor(
                minimumJavaMajorVersion == null ? 17 : minimumJavaMajorVersion
        );
    }

    private static class UpgradeJavaVersionVisitor extends YamlVisitor<ExecutionContext> {
        private static final JsonPathMatcher javaVersion = new JsonPathMatcher("..steps[?(@.uses =~ 'actions/setup-java@v*.*')].with.java-version");

        private final int minimumJavaMajorVersion;

        public UpgradeJavaVersionVisitor(int minimumJavaMajorVersion) {
            this.minimumJavaMajorVersion = minimumJavaMajorVersion;
        }

        @Override
        public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            if (!javaVersion.matches(getCursor())) {
                return super.visitMappingEntry(entry, ctx);
            }

            Yaml.Scalar currentValue = (Yaml.Scalar) entry.getValue();

            // specific versions are allowed by `actions/setup-java`
            String[] currentVersionParts = currentValue.getValue().split("\\.");
            int currentMajorVersion = Integer.parseInt(currentVersionParts[0]);
            if (currentMajorVersion >= minimumJavaMajorVersion) {
                return super.visitMappingEntry(entry, ctx);
            }

            return super.visitMappingEntry(
                    entry.withValue(currentValue.withValue(String.valueOf(minimumJavaMajorVersion))),
                    ctx
            );
        }
    }
}
