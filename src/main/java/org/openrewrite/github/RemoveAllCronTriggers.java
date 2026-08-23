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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.yaml.DeleteKey;

import java.time.Duration;

@EqualsAndHashCode(callSuper = false)
@Getter
public class RemoveAllCronTriggers extends Recipe {

    @Option(displayName = "Workflow files to match",
            description = "Matches one or more workflows to update. Defaults to `*.{yml,yaml}`",
            required = false,
            example = "build.yml")
    @Nullable
    private final String workflowFileMatcher;

    public RemoveAllCronTriggers() {
        this(null);
    }

    @JsonCreator
    public RemoveAllCronTriggers(@JsonProperty("workflowFileMatcher") @Nullable String workflowFileMatcher) {
        this.workflowFileMatcher = workflowFileMatcher;
    }

    final String displayName = "Remove all cron triggers";

    final String description = "Removes all cron triggers from a workflow.";

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes( 1 );

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String path = StringUtils.isBlank(workflowFileMatcher) ? ".github/workflows/*.{yml,yaml}" : ".github/workflows/" + workflowFileMatcher;
        return Preconditions.check(new FindSourceFiles(path),
                new DeleteKey("$.on.schedule", null).getVisitor());
    }
}
