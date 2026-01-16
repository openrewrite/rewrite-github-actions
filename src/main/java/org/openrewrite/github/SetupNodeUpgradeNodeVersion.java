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

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class SetupNodeUpgradeNodeVersion extends Recipe {

    @Option(displayName = "Minimum major Node.js version (defaults to 24)",
            example = "24",
            required = false)
    @Nullable
    Integer minimumNodeMajorVersion;

    String displayName = "Upgrade `actions/setup-node` `node-version`";

    String description = "Update the Node.js version used by `actions/setup-node` if it is below the expected version number.";

    Set<String> tags = new LinkedHashSet<>( Arrays.asList( "github", "nodejs", "deprecation" ) );

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsWorkflow(), new UpgradeNodeVersionVisitor(
                minimumNodeMajorVersion == null ? 24 : minimumNodeMajorVersion
        ));
    }

    @AllArgsConstructor
    private static class UpgradeNodeVersionVisitor extends YamlVisitor<ExecutionContext> {
        private static final JsonPathMatcher nodeVersion = new JsonPathMatcher("..steps[?(@.uses =~ 'actions/setup-node@v*.*')].with.node-version");
        private static final Pattern nodeVersionPattern = Pattern.compile("([0-9]+)(\\.[0-9]+)*([-+].*)?");

        private final int minimumNodeMajorVersion;

        @Override
        public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            if (!nodeVersion.matches(getCursor())) {
                return super.visitMappingEntry(entry, ctx);
            }

            Yaml.Scalar currentValue = (Yaml.Scalar) entry.getValue();

            // specific versions are allowed by `actions/setup-node`
            Matcher matcher = nodeVersionPattern.matcher(currentValue.getValue());
            if (!matcher.matches()) {
                return super.visitMappingEntry(entry, ctx);
            }

            int currentMajorVersion;
            try {
                currentMajorVersion = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ex) {
                return super.visitMappingEntry(entry, ctx);
            }

            if (currentMajorVersion >= minimumNodeMajorVersion) {
                return super.visitMappingEntry(entry, ctx);
            }

            return super.visitMappingEntry(
                    entry.withValue(currentValue.withValue(String.valueOf(minimumNodeMajorVersion))),
                    ctx
            );
        }
    }
}
