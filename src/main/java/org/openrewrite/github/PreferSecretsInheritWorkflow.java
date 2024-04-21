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
import static org.openrewrite.marker.Markers.EMPTY;
import static org.openrewrite.yaml.tree.Yaml.Scalar.Style.PLAIN;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;
import org.openrewrite.yaml.tree.Yaml.Scalar;

@Value
@EqualsAndHashCode(callSuper = false)
public class PreferSecretsInheritWorkflow extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use `secrets: inherit` if possible";
    }

    @Override
    public String getDescription() {
        return "Pass all secrets to a reusable workflow using `secrets: inherit`. See [Simplify using secrets with reusable workflows](https://github"
               + ".blog/changelog/2022-05-03-github-actions-simplify-using-secrets-with-reusable-workflows/) for "
               + "details.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        final JsonPathMatcher secrets = new JsonPathMatcher("$.jobs..secrets");

        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"), new YamlVisitor<ExecutionContext>() {

            private static final String USE_INHERIT = "USE_INHERIT";

            @Override
            public Yaml visitMapping(final Yaml.Mapping mapping, final ExecutionContext ctx) {
                Cursor parentEntry = getCursor().getParent();

                if (parentEntry != null && secrets.matches(parentEntry) &&
                    mapping.getEntries().stream().allMatch(this::isUntransformedSecret)) {
                    getCursor().putMessageOnFirstEnclosing(Yaml.Mapping.Entry.class, USE_INHERIT, true);
                }

                return super.visitMapping(mapping, ctx);
            }

            @Override
            public Yaml visitMappingEntry(final Yaml.Mapping.Entry entry, final ExecutionContext executionContext) {
                Yaml e = super.visitMappingEntry(entry, executionContext);

                if (getCursor().getMessage(USE_INHERIT, false)) {
                    Scalar inheritValue = new Scalar(Tree.randomId(), " ", EMPTY, PLAIN, null, "inherit");
                    return entry.withValue(inheritValue);
                }

                return e;
            }

            private boolean isUntransformedSecret(final Yaml.Mapping.Entry entry) {
                String key = entry.getKey().getValue();
                String secretReferenceRegex = "\\$\\{\\{\\s*secrets." + key + "\\s*}}";

                Yaml.Block value = entry.getValue();
                return (value instanceof Scalar && ((Scalar) value).getValue().matches(secretReferenceRegex));
            }
        });
    }
}
