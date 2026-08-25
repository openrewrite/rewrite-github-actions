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
    // A leading string literal alternative consumes `'...'` so that only the unquoted group 1 calls are replaced
    private static final Pattern ALWAYS_CALL = Pattern.compile("'(?:[^']|'')*'|((?<![A-Za-z0-9_.])always\\s*\\(\\s*\\)(?![A-Za-z0-9_]))");
    private static final Pattern ONLY_ALWAYS = Pattern.compile("\\s*(?:always\\s*\\(\\s*\\)|\\$\\{\\{\\s*always\\s*\\(\\s*\\)\\s*}})\\s*");
    private static final String REPLACEMENT = "success() || failure()";
    private static final String PARENTHESIZED_REPLACEMENT = "(" + REPLACEMENT + ")";
    private static final BlockScalar.Matcher BLOCK_SCALAR = new BlockScalar.Matcher();
    private static final JsonPathMatcher STEP_CONDITION = new JsonPathMatcher("$..steps[*].if");
    private static final JsonPathMatcher JOB_CONDITION = new JsonPathMatcher("$.jobs.*.if");

    @Getter
    final String displayName = "Replace `always()` with `success() || failure()`";

    @Getter
    final String description = "Replace `always()` in GitHub Actions job and step conditions with `success() || failure()` " +
            "so that canceled workflows do not continue running or hang until they time out. Note that teardown steps " +
            "deliberately using `always()` to still run on cancellation will no longer run.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsFile(), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                if (!"if".equals(e.getKey().getValue()) || !(e.getValue() instanceof Yaml.Scalar)) {
                    return e;
                }
                Yaml.Scalar condition = (Yaml.Scalar) e.getValue();
                if (!condition.getValue().contains("always") ||
                        !(STEP_CONDITION.matches(getCursor()) || JOB_CONDITION.matches(getCursor()))) {
                    return e;
                }
                Optional<BlockScalar> blockScalar = BLOCK_SCALAR.get(condition, getCursor());
                String value = blockScalar.isPresent() ? blockScalar.get().getBody() : condition.getValue();
                // `''` is YAML's escape for a quote, not a closed expression string
                String updated = condition.getStyle() == Yaml.Scalar.Style.SINGLE_QUOTED ?
                        replaceAlways(value.replace("''", "'")).replace("'", "''") :
                        replaceAlways(value);
                if (!value.equals(updated)) {
                    return e.withValue(blockScalar.isPresent() ?
                            blockScalar.get().withBody(updated) : condition.withValue(updated));
                }
                return e;
            }
        });
    }

    private static String replaceAlways(String condition) {
        String replacement = ONLY_ALWAYS.matcher(condition).matches() ? REPLACEMENT : PARENTHESIZED_REPLACEMENT;
        Matcher matcher = ALWAYS_CALL.matcher(condition);
        StringBuffer updated = new StringBuffer();
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                matcher.appendReplacement(updated, replacement);
            }
        }
        return matcher.appendTail(updated).toString();
    }
}
