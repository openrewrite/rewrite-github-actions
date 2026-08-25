/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.trait.BlockScalar;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReplaceAlwaysWithSuccessOrFailure extends Recipe {
    private static final Pattern ALWAYS_CALL = Pattern.compile("(?<![A-Za-z0-9_.])always\\s*\\(\\s*\\)(?![A-Za-z0-9_])");
    private static final String REPLACEMENT = "success() || failure()";

    @Getter
    final String displayName = "Replace `always()` with `success() || failure()`";

    @Getter
    final String description = "Replace `always()` in GitHub Actions job and step conditions with `success() || failure()` " +
            "so that canceled workflows do not continue running or hang until they time out.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsFile(), new YamlIsoVisitor<ExecutionContext>() {
            private final JsonPathMatcher jobCondition = new JsonPathMatcher("$.jobs.*.if");
            private final JsonPathMatcher stepCondition = new JsonPathMatcher("$..steps[*].if");

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                if ((jobCondition.matches(getCursor()) || stepCondition.matches(getCursor())) &&
                        e.getValue() instanceof Yaml.Scalar) {
                    Yaml.Scalar condition = (Yaml.Scalar) e.getValue();
                    Optional<BlockScalar> blockScalar = new BlockScalar.Matcher().get(condition, getCursor());
                    String value = blockScalar.isPresent() ? blockScalar.get().getBody() : condition.getValue();
                    String updated = replaceAlways(value);
                    if (!value.equals(updated)) {
                        return e.withValue(blockScalar.isPresent() ?
                                blockScalar.get().withBody(updated) : condition.withValue(updated));
                    }
                }
                return e;
            }
        });
    }

    private static String replaceAlways(String condition) {
        Matcher matcher = ALWAYS_CALL.matcher(condition);
        String expression = condition.trim();
        if (expression.startsWith("${{") && expression.endsWith("}}")) {
            expression = expression.substring(3, expression.length() - 2).trim();
        }

        String replacement = ALWAYS_CALL.matcher(expression).matches() ?
                REPLACEMENT : "(" + REPLACEMENT + ")";
        StringBuilder updated = null;
        int lastMatchEnd = 0;
        while (matcher.find()) {
            if (isInsideStringLiteral(condition, matcher.start())) {
                continue;
            }
            if (updated == null) {
                updated = new StringBuilder(condition.length() + replacement.length());
            }
            updated.append(condition, lastMatchEnd, matcher.start()).append(replacement);
            lastMatchEnd = matcher.end();
        }
        return updated == null ? condition : updated.append(condition, lastMatchEnd, condition.length()).toString();
    }

    private static boolean isInsideStringLiteral(String expression, int offset) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int i = 0; i < offset; i++) {
            char current = expression.charAt(i);
            if (singleQuoted) {
                if (current == '\'' && i + 1 < offset && expression.charAt(i + 1) == '\'') {
                    i++;
                } else if (current == '\'') {
                    singleQuoted = false;
                }
            } else if (doubleQuoted) {
                if (current == '\\' && i + 1 < offset) {
                    i++;
                } else if (current == '"') {
                    doubleQuoted = false;
                }
            } else if (current == '\'') {
                singleQuoted = true;
            } else if (current == '"') {
                doubleQuoted = true;
            }
        }
        return singleQuoted || doubleQuoted;
    }
}
