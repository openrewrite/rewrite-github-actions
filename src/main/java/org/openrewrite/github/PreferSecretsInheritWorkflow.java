/*
 * Copyright 2025 the original author or authors.
 *
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
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

@Value
@EqualsAndHashCode(callSuper = false)
public class PreferSecretsInheritWorkflow extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use `secrets: inherit` if possible";
    }

    @Override
    public String getDescription() {
        return "Pass all secrets to a reusable workflow using `secrets: inherit`. See " +
               "[Simplify using secrets with reusable workflows]" +
               "(https://github.blog/changelog/2022-05-03-github-actions-simplify-using-secrets-with-reusable-workflows/)" +
               " for details.";
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
                            Scalar inheritValue = new Scalar(Tree.randomId(), " ", EMPTY, PLAIN, null, "inherit");
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
