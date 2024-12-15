/*
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.*;
import org.openrewrite.yaml.ChangeValue;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeActionVersion extends Recipe {
    @Option(displayName = "Action",
            description = "Name of the action to update.",
            example = "actions/setup-java")
    String action;

    @Option(displayName = "Version",
            description = "Version to use.",
            example = "v4")
    String version;

    @Override
    public String getDisplayName() {
        return "Change GitHub Action version";
    }

    @Override
    public String getDescription() {
        return "Change the version of a GitHub Action in any `.github/workflows/*.yml` file.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml"),
                new ChangeValue(
                        "$.jobs..steps[?(@.uses =~ '" + action + "(?:@.+)?')].uses",
                        action + '@' + version, null).getVisitor());
    }
}
