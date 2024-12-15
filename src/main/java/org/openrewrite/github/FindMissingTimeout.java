/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.*;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.search.FindKey;
import org.openrewrite.yaml.tree.Yaml;

public class FindMissingTimeout extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find jobs missing timeout";
    }

    @Override
    public String getDescription() {
        return "Find GitHub Actions jobs missing a timeout.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry job = super.visitMappingEntry(entry, ctx);
                if (new JsonPathMatcher("$.jobs.*.*").matches(getCursor()) &&
                    FindKey.find(job, "$..timeout-minutes").isEmpty()) {
                    return SearchResult.found(job, "missing: $.jobs.*.timeout-minutes");
                }
                return job;
            }
        });
    }
}
