/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.ExecutionContext;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class SetupJavaDistributionReplacerVisitor extends YamlIsoVisitor<ExecutionContext> {

    static final JsonPathMatcher DISTRIBUTION_MATCHER = new JsonPathMatcher("..steps[?(@.uses =~ 'actions/setup-java@v[234].*')].with.distribution");

    private final List<String> originalDistributions;
    private final String newDistribution;

    @Override
    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
        if (DISTRIBUTION_MATCHER.matches(getCursor()) && this.originalDistributions.contains(((Yaml.Scalar) entry.getValue()).getValue())) {
            return super.visitMappingEntry(entry.withValue(((Yaml.Scalar) entry.getValue()).withValue(this.newDistribution)), ctx);
        }
        return super.visitMappingEntry(entry, ctx);
    }
}
