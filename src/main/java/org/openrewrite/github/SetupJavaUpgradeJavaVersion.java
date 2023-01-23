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
