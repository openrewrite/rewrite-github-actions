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
package org.openrewrite.github.table;

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

public class UnresolvedActionShas extends DataTable<UnresolvedActionShas.Row> {

    public UnresolvedActionShas(Recipe recipe) {
        super(recipe, "Unresolved action SHAs",
                "Action references that were eligible for pinning, but not found in the built-in mapping of known action SHAs.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The workflow or action file containing the reference.")
        String sourcePath;

        @Column(displayName = "Action",
                description = "The action path, such as `owner/repo` or `owner/repo/subpath`.")
        String action;

        @Column(displayName = "Reference",
                description = "The tag or branch the action is referenced by.")
        String ref;
    }
}
