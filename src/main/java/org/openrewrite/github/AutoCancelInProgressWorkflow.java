/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.github;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

@Value
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("ConcatenationWithEmptyString")
public class AutoCancelInProgressWorkflow extends Recipe {
    @Option(displayName = "Optional access token",
            description = "Optionally provide the key name of a repository or organization secret that contains a GitHub personal access token with permission to cancel workflows.",
            required = false,
            example = "WORKFLOWS_ACCESS_TOKEN")
    @Nullable
    String accessToken;

    @Override
    public String getDisplayName() {
        return "Cancel in-progress workflow when it is triggered again";
    }

    @Override
    public String getDescription() {
        return "When a workflow is already running and would be triggered again, cancel the existing workflow. " +
                "See [`styfle/cancel-workflow-action`](https://github.com/styfle/cancel-workflow-action) for details.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JsonPathMatcher firstStep = new JsonPathMatcher("$.jobs.build.steps[:1].uses");
        JsonPathMatcher jobSteps = new JsonPathMatcher("$.jobs.build.steps.*");

        String userProvidedAccessTokenTemplate = "" +
                "- uses: styfle/cancel-workflow-action@0.9.1\n" +
                "  with:\n" +
                "    access_token: ${{ secrets." + accessToken + " }}";

        String defaultAccessTokenTemplate = "" +
                "- uses: styfle/cancel-workflow-action@0.9.1\n" +
                "  with:\n" +
                "    access_token: ${{ github.token }}";

        return Preconditions.check(new HasSourcePath(".github/workflows/*.yml"), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                if (firstStep.matches(getCursor()) && (!(entry.getValue() instanceof Yaml.Scalar) ||
                        !((Yaml.Scalar) entry.getValue()).getValue().contains("cancel-workflow-action"))) {
                    getCursor().dropParentUntil(Yaml.Sequence.class::isInstance).putMessage("ADD_STEP", true);
                }
                return super.visitMappingEntry(entry, ctx);
            }

            @Override
            public Yaml.Sequence visitSequence(Yaml.Sequence sequence, ExecutionContext ctx) {
                Yaml.Sequence s = super.visitSequence(sequence, ctx);
                if (jobSteps.matches(getCursor()) && Boolean.TRUE.equals(getCursor().getMessage("ADD_STEP"))) {
                    Yaml.Documents documents = new YamlParser()
                            .parse(ctx, StringUtils.isNullOrEmpty(accessToken) ? defaultAccessTokenTemplate : userProvidedAccessTokenTemplate)
                            .map(Yaml.Documents.class::cast)
                            .findFirst()
                            .get();
                    Yaml.Sequence.Entry cancelWorkflowAction = ((Yaml.Sequence) documents.getDocuments().get(0).getBlock()).getEntries().get(0);
                    cancelWorkflowAction = autoFormat(cancelWorkflowAction.withPrefix("\n"), ctx, getCursor());
                    return s.withEntries(ListUtils.concat(cancelWorkflowAction, s.getEntries()));
                }
                return s;
            }
        });
    }
}
