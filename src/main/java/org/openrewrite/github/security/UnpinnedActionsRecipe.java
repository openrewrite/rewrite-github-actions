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
package org.openrewrite.github.security;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.github.util.ActionStep;
import org.openrewrite.marker.SearchResult;

import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class UnpinnedActionsRecipe extends Recipe {

    private static final Pattern UNPINNED_ACTION_PATTERN = Pattern.compile(
            "^([^/@]+/[^/@]+)(@(main|master|HEAD|latest|v?\\d+(\\.\\d+)*(\\.\\d+)*))??$"
    );

    @Override
    public String getDisplayName() {
        return "Pin GitHub Actions to specific commits";
    }

    @Override
    public String getDescription() {
        return "Pin GitHub Actions to specific commit SHAs for security and reproducibility. " +
                "Actions pinned to tags or branches can be changed by the action author, " +
                "while SHA pins are immutable. " +
                "Based on [zizmor's unpinned-uses audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/unpinned_uses.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml"),
                new ActionStep.Matcher().asVisitor((actionStep, ctx) -> {
                    if (isUnpinned(actionStep)) {
                        String actionRef = actionStep.getActionRef();
                        return SearchResult.found(actionStep.getTree(),
                                "Action '" + actionRef + "' is not pinned to a commit SHA. " +
                                        "Consider pinning to a specific commit for security and reproducibility.");
                    }
                    return actionStep.getTree();
                })
        );
    }

    private static boolean isUnpinned(ActionStep actionStep) {
        String actionRef = actionStep.getActionRef();
        if (actionRef == null) {
            return false;
        }

        // Skip local actions (start with ./)
        if (actionRef.startsWith("./")) {
            return false;
        }

        // Skip Docker actions (start with docker://)
        if (actionRef.startsWith("docker://")) {
            return false;
        }

        // If it's already pinned to a SHA, it's not unpinned
        if (actionStep.isVersionPinned()) {
            return false;
        }

        // Check if version exists
        String version = actionStep.getActionVersion();
        if (version == null) {
            // No @ symbol means no version specified at all
            return true;
        }

        // If it matches unpinned patterns (main, master, HEAD, latest, or version tags), it's unpinned
        return UNPINNED_ACTION_PATTERN.matcher(actionRef).matches();
    }
}
