/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.*;
import org.openrewrite.yaml.DeleteKey;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.search.FindKey;
import org.openrewrite.yaml.tree.Yaml;

public class SetupJavaCaching extends Recipe {
    @Override
    public String getDisplayName() {
        return "Setup Java dependency caching";
    }

    @Override
    public String getDescription() {
        return "GitHub actions supports dependency caching on Maven and Gradle projects. See the [blog post](https://github.blog/changelog/2021-08-30-github-actions-setup-java-now-supports-dependency-caching/).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"), new YamlVisitor<ExecutionContext>() {
            @Override
            public Yaml visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                Yaml.Documents d = documents;
                if (!FindKey.find(documents, "$.jobs..steps[?(@.run =~ '.*gradle.*')]").isEmpty()) {
                    d = (Yaml.Documents) new MergeYaml("$.jobs..steps[?(@.uses =~ 'actions/setup-java(?:@v.+)?')]",
                            "" +
                                    "with:\n" +
                                    "  cache: 'gradle'", true, null, null)
                            .getVisitor().visitNonNull(d, ctx);
                }
                if (!FindKey.find(documents, "$.jobs..steps[?(@.run =~ '.*mvn.*')]").isEmpty()) {
                    d = (Yaml.Documents) new MergeYaml("$.jobs..steps[?(@.uses =~ 'actions/setup-java(?:@v.+)?')]",
                            "" +
                                    "with:\n" +
                                    "  cache: 'maven'", true, null, null)
                            .getVisitor().visitNonNull(d, ctx);
                }
                if (d != documents) {
                    d = (Yaml.Documents) new DeleteKey("$.jobs..steps[?(@.uses =~ 'actions/cache(?:@v.+)?')]", null)
                            .getVisitor().visitNonNull(d, ctx);
                }
                return d;
            }
        });
    }
}
