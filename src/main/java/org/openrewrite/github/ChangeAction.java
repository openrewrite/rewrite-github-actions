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
public class ChangeAction extends Recipe {
    @Option(displayName = "Action",
            description = "Name of the action to match.",
            example = "gradle/wrapper-validation-action")
    String oldAction;

    @Option(displayName = "Action",
            description = "Name of the action to use instead.",
            example = "gradle/actions/wrapper-validation")
    String newAction;

    @Option(displayName = "Version",
            description = "New version to use.",
            example = "v3")
    String newVersion;

    @Override
    public String getDisplayName() {
        return "Change GitHub Action";
    }

    @Override
    public String getDescription() {
        return "Change a GitHub Action in any `.github/workflows/*.yml` file.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml"),
                new ChangeValue(
                        "$.jobs..steps[?(@.uses =~ '" + oldAction + "(?:@.+)?')].uses",
                        newAction + '@' + newVersion, null).getVisitor());
    }
}
