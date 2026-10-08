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
package org.openrewrite.github;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveRunner extends Recipe {
    private static final JsonPathMatcher RUNS_ON = new JsonPathMatcher("$.jobs.*.runs-on");
    private static final Pattern TRAILING_COMMENT = Pattern.compile("^[ \\t]*#[^\\n]*");

    @Option(displayName = "Job name",
            description = "The name of the job to update, use `*` to affect all the workflow jobs.",
            example = "build")
    String jobName;

    @Option(displayName = "Runner",
            description = "The runner label to remove. Labels are compared case-insensitively, as GitHub does.",
            example = "self-hosted")
    String runner;

    String displayName = "Remove a runner from a job";

    String description = "Removes a runner label from the `runs-on` of a job, leaving the remaining runner labels untouched. " +
            "Both sequences (`runs-on: [self-hosted, linux]`) and the `labels` of a runner group are supported. " +
            "The runner is never removed when it is the only one left, as a job requires at least one runner.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsWorkflow(), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                if (!RUNS_ON.matches(getCursor()) || !isTargetJob()) {
                    return e;
                }
                if (e.getValue() instanceof Yaml.Sequence) {
                    return e.withValue(removeFrom((Yaml.Sequence) e.getValue()));
                }
                if (e.getValue() instanceof Yaml.Mapping) {
                    return e.withValue(removeFromLabels((Yaml.Mapping) e.getValue()));
                }
                return e;
            }

            private boolean isTargetJob() {
                if ("*".equals(jobName)) {
                    return true;
                }
                Object job = getCursor().getParentTreeCursor().getParentTreeCursor().getValue();
                return job instanceof Yaml.Mapping.Entry && jobName.equals(((Yaml.Mapping.Entry) job).getKey().getValue());
            }
        });
    }

    private Yaml.Sequence removeFrom(Yaml.Sequence sequence) {
        List<Yaml.Sequence.Entry> entries = sequence.getEntries();
        List<Yaml.Sequence.Entry> remaining = new ArrayList<>(entries.size());
        boolean previousRemoved = false;
        for (Yaml.Sequence.Entry entry : entries) {
            if (isRunner(entry.getBlock())) {
                previousRemoved = true;
            } else {
                remaining.add(previousRemoved ? entry.withPrefix(withoutTrailingComment(entry.getPrefix())) : entry);
                previousRemoved = false;
            }
        }
        if (remaining.size() == entries.size() || remaining.isEmpty()) {
            return sequence;
        }

        Yaml.Sequence.Entry originalFirst = entries.get(0);
        if (remaining.get(0) != originalFirst) {
            remaining.set(0, remaining.get(0)
                    .withPrefix(originalFirst.getPrefix())
                    .withBlock(remaining.get(0).getBlock().withPrefix(originalFirst.getBlock().getPrefix())));
        }
        int last = remaining.size() - 1;
        remaining.set(last, remaining.get(last).withTrailingCommaPrefix(entries.get(entries.size() - 1).getTrailingCommaPrefix()));
        return sequence.withEntries(remaining);
    }

    private Yaml.Mapping removeFromLabels(Yaml.Mapping runsOn) {
        List<Yaml.Mapping.Entry> entries = runsOn.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            Yaml.Mapping.Entry entry = entries.get(i);
            if (!"labels".equals(entry.getKey().getValue())) {
                continue;
            }
            if (consistsOfRunner(entry.getValue())) {
                if (!hasGroup(runsOn)) {
                    return runsOn;
                }
                List<Yaml.Mapping.Entry> updated = new ArrayList<>(entries);
                updated.remove(i);
                if (i == 0) {
                    updated.set(0, updated.get(0).withPrefix(entry.getPrefix()));
                } else if (i < updated.size()) {
                    updated.set(i, updated.get(i).withPrefix(withoutTrailingComment(updated.get(i).getPrefix())));
                }
                return runsOn.withEntries(updated);
            }
            if (entry.getValue() instanceof Yaml.Sequence) {
                int index = i;
                return runsOn.withEntries(ListUtils.map(entries, (idx, e) ->
                        idx == index ? e.withValue(removeFrom((Yaml.Sequence) e.getValue())) : e));
            }
            return runsOn;
        }
        return runsOn;
    }

    private static String withoutTrailingComment(String prefix) {
        return TRAILING_COMMENT.matcher(prefix).replaceFirst("");
    }

    private static boolean hasGroup(Yaml.Mapping runsOn) {
        for (Yaml.Mapping.Entry entry : runsOn.getEntries()) {
            if ("group".equals(entry.getKey().getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean consistsOfRunner(Yaml.Block labels) {
        if (labels instanceof Yaml.Sequence) {
            List<Yaml.Sequence.Entry> entries = ((Yaml.Sequence) labels).getEntries();
            return !entries.isEmpty() && entries.stream().allMatch(e -> isRunner(e.getBlock()));
        }
        return isRunner(labels);
    }

    private boolean isRunner(Yaml.Block block) {
        return block instanceof Yaml.Scalar && runner.equalsIgnoreCase(((Yaml.Scalar) block).getValue());
    }
}
