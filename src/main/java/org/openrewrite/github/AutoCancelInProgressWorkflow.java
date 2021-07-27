package org.openrewrite.github;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

@Value
@EqualsAndHashCode(callSuper = true)
public class AutoCancelInProgressWorkflow extends Recipe {

    @Option(displayName = "Access token",
            description = "A repository or organization secret that contains a Github personal access token with permission to cancel workflows.",
            example = "WORKFLOWS_ACCESS_TOKEN")
    String accessToken;

    @Override
    public String getDisplayName() {
        return "Cancel in-progress workflow when it is triggered again";
    }

    @Override
    public String getDescription() {
        return "When a workflow is already running and would be triggered again, cancel the existing workflow.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new HasSourcePath<>("**/.github/workflows/*.yml");
    }

    @Override
    protected YamlVisitor<ExecutionContext> getVisitor() {
        JsonPathMatcher firstStep = new JsonPathMatcher("$.jobs.build.steps[:1].uses");
        JsonPathMatcher jobSteps = new JsonPathMatcher("$.jobs.build.steps[]");
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext executionContext) {
                if (firstStep.matches(getCursor()) && (!(entry.getValue() instanceof Yaml.Scalar) ||
                        !((Yaml.Scalar) entry.getValue()).getValue().contains("cancel-workflow-action"))) {
                    getCursor().dropParentUntil(Yaml.Sequence.class::isInstance).putMessage("ADD_STEP", true);
                }
                return super.visitMappingEntry(entry, executionContext);
            }

            @Override
            public Yaml.Sequence visitSequence(Yaml.Sequence sequence, ExecutionContext ctx) {
                Yaml.Sequence s = super.visitSequence(sequence, ctx);
                if (jobSteps.encloses(getCursor()) && Boolean.TRUE.equals(getCursor().getMessage("ADD_STEP"))) {
                    Yaml.Documents documents = new YamlParser().parse(ctx, "- uses: styfle/cancel-workflow-action@0.8.0\n" +
                            "  with:\n" +
                            "    access_token: ${{secrets." + accessToken + "}}").get(0);
                    Yaml.Sequence.Entry cancelWorkflowAction = ((Yaml.Sequence) documents.getDocuments().get(0).getBlock()).getEntries().get(0);
                    cancelWorkflowAction = autoFormat(cancelWorkflowAction.withPrefix("\n"), ctx, getCursor());
                    return s.withEntries(ListUtils.concat(cancelWorkflowAction, s.getEntries()));
                }
                return s;
            }
        };
    }
}
