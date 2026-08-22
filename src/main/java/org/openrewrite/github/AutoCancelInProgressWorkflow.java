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

import lombok.Getter;
import org.openrewrite.Recipe;
import org.openrewrite.yaml.MergeYaml;

import java.util.List;

import static java.util.Collections.singletonList;

public class AutoCancelInProgressWorkflow extends Recipe {

    private static final String CONCURRENCY = "concurrency:\n" +
            "  group: ${{ github.workflow }}-${{ github.ref }}\n" +
            "  cancel-in-progress: ${{ github.ref != 'refs/heads/main' }}";

    @Getter
    final String displayName = "Cancel in-progress workflow when it is triggered again";

    @Getter
    final String description = "When a workflow is already running and would be triggered again, cancel the existing workflow, " +
            "through the native [`concurrency`](https://docs.github.com/en/actions/using-jobs/using-concurrency) property. " +
            "Runs on the default branch are not cancelled.";

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(new MergeYaml(
                "$",
                CONCURRENCY,
                true,
                null,
                ".github/workflows/*.{yml,yaml}",
                MergeYaml.InsertMode.Before,
                "jobs",
                null));
    }
}
