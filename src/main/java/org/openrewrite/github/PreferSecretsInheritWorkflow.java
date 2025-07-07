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
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;
import org.openrewrite.yaml.tree.Yaml.Scalar;

import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Value;

import static org.openrewrite.marker.Markers.EMPTY;
import static org.openrewrite.yaml.tree.Yaml.Scalar.Style.PLAIN;

@EqualsAndHashCode(callSuper = false)
@Value
public class PreferSecretsInheritWorkflow extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use `secrets: inherit` if possible";
    }

    @Override
    public String getDescription() {
        return "Pass all secrets to a reusable workflow using `secrets: inherit`. See " +
               "[Simplify using secrets with reusable workflows]" +
               "(https://github.blog/changelog/2022-05-03-github-actions-simplify-using-secrets-with-reusable-workflows/) " +
               "for details.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        final JsonPathMatcher secrets = new JsonPathMatcher("$.jobs..secrets");

        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml").getVisitor(),
                new YamlIsoVisitor<ExecutionContext>() {
                    private static final String USE_INHERIT = "USE_INHERIT";

                    @Override
                    public Yaml.Mapping visitMapping(final Yaml.Mapping mapping, final ExecutionContext ctx) {
                        Cursor parentEntry = getCursor().getParent();
                        if (parentEntry != null && secrets.matches(parentEntry)) {
                            boolean allUntransformed = mapping.getEntries().stream().allMatch(this::isUntransformedSecret);
                            if (allUntransformed) {
                                getCursor().putMessageOnFirstEnclosing(Yaml.Mapping.Entry.class, USE_INHERIT, true);
                            }
                        }

                        return super.visitMapping(mapping, ctx);
                    }

                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(final Yaml.Mapping.Entry entry, final ExecutionContext ctx) {
                        Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);

                        if (getCursor().getMessage(USE_INHERIT, false)) {
                            Scalar inheritValue = new Scalar(Tree.randomId(), " ", EMPTY, PLAIN, null, null, "inherit");
                            return e.withValue(inheritValue);
                        }

                        return e;
                    }

                    private boolean isUntransformedSecret(final Yaml.Mapping.Entry entry) {
                        String key = entry.getKey().getValue();
                        Pattern secretPattern = Pattern.compile("\\$\\{\\{\\s*secrets." + key + "\\s*}}");

                        Yaml.Block value = entry.getValue();
                        return (value instanceof Scalar && secretPattern.matcher(((Scalar) value).getValue()).matches());
                    }
                });
    }
}
