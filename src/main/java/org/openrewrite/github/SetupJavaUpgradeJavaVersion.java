/*
 * Copyright 2025 the original author or authors.
 *
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.*;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;

@Value
@EqualsAndHashCode(callSuper = false)
public class SetupJavaUpgradeJavaVersion extends Recipe {

    @Option(displayName = "Minimum major Java version (defaults to 21)",
            example = "21",
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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"), new UpgradeJavaVersionVisitor(
                minimumJavaMajorVersion == null ? 21 : minimumJavaMajorVersion
        ));
    }

    @AllArgsConstructor
    private static class UpgradeJavaVersionVisitor extends YamlVisitor<ExecutionContext> {
        private static final JsonPathMatcher javaVersion = new JsonPathMatcher("..steps[?(@.uses =~ 'actions/setup-java@v*.*')].with.java-version");
        private static final Pattern javaVersionPattern = Pattern.compile("([0-9]+)(\\.[0-9]+)*([-+].*)?");

        private final int minimumJavaMajorVersion;

        @Override
        public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            if (!javaVersion.matches(getCursor())) {
                return super.visitMappingEntry(entry, ctx);
            }

            Yaml.Scalar currentValue = (Yaml.Scalar) entry.getValue();

            // specific versions are allowed by `actions/setup-java`
            Matcher matcher = javaVersionPattern.matcher(currentValue.getValue());
            if (!matcher.matches()) {
                return super.visitMappingEntry(entry, ctx);
            }

            int currentMajorVersion;
            try {
                currentMajorVersion = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ex) {
                return super.visitMappingEntry(entry, ctx);
            }

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
