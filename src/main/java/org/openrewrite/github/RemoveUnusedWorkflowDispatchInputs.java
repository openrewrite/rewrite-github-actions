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

import org.openrewrite.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

import static org.openrewrite.Tree.randomId;

public class RemoveUnusedWorkflowDispatchInputs extends Recipe {

    private static final Pattern INPUT_USAGE_PATTERN = Pattern.compile("(?:github *[.] *event *[.] *inputs *[.] *(\\w+)|inputs *[.] *(\\w+))");
    private static final JsonPathMatcher WORKFLOW_DISPATCH_INPUTS_MATCHER = new JsonPathMatcher("$.on.workflow_dispatch.inputs");

    @Override
    public String getDisplayName() {
        return "Remove unused workflow dispatch inputs";
    }

    @Override
    public String getDescription() {
        return "Remove workflow_dispatch inputs that are not referenced anywhere in the workflow file.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                Set<String> definedInputs = new HashSet<>();
                Set<String> usedInputs = new HashSet<>();

                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                        if (WORKFLOW_DISPATCH_INPUTS_MATCHER.matches(getCursor())) {
                            Yaml.Block value = e.getValue();
                            if (value instanceof Yaml.Mapping) {
                                Yaml.Mapping inputs = (Yaml.Mapping) value;
                                for (Yaml.Mapping.Entry inputEntry : inputs.getEntries()) {
                                    if (inputEntry.getKey() instanceof Yaml.Scalar) {
                                        definedInputs.add((inputEntry.getKey()).getValue());
                                    }
                                }
                            }
                        }
                        return e;
                    }

                    @Override
                    public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                        String value = scalar.getValue();

                        // This might have some false negatives as one can use a string like this, but this should be rare
                        // and no harm is done in such a case.
                        Matcher matcher = INPUT_USAGE_PATTERN.matcher(value);
                        while (matcher.find()) {
                            String inputName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                            if (inputName != null) {
                                usedInputs.add(inputName);
                            }
                        }

                        return super.visitScalar(scalar, ctx);
                    }
                }.visit(document, ctx);

                if (definedInputs.size() == usedInputs.size()) {
                    return document;
                }

                return (Yaml.Document) Objects.requireNonNull(new YamlIsoVisitor<ExecutionContext>() {

                    @Override
                    public Yaml.Mapping.@Nullable Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        if (WORKFLOW_DISPATCH_INPUTS_MATCHER.matches(getCursor().getParent().getParent())) {
                            if (entry.getKey() instanceof Yaml.Scalar) {
                                String inputName = (entry.getKey()).getValue();
                                if (definedInputs.contains(inputName) && !usedInputs.contains(inputName)) {
                                    return null;
                                }
                            }
                        }
                        return super.visitMappingEntry(entry, ctx);
                    }

                    @Override
                    public @Nullable Yaml postVisit(Yaml tree, ExecutionContext ctx) {
                        if (tree instanceof Yaml.Mapping.Entry) {
                            Yaml.Mapping.Entry entry = (Yaml.Mapping.Entry) tree;
                            if ("workflow_dispatch".equals(entry.getKey().getValue())) {
                                Yaml.Mapping inputs = (Yaml.Mapping) entry.getValue();
                                if (inputs.getEntries().size() == 1) {
                                    Yaml.Mapping.Entry inputsEntry = inputs.getEntries().get(0);
                                    if (inputsEntry.getValue() instanceof Yaml.Mapping && ((Yaml.Mapping) inputsEntry.getValue()).getEntries().isEmpty()) {
                                        return entry.withValue(new Yaml.Mapping(
                                                randomId(),
                                                Markers.EMPTY,
                                                " ",
                                                Collections.emptyList(),
                                                "",
                                                null,
                                                null
                                        ));
                                    }
                                }
                            }
                        }
                        return super.postVisit(tree, ctx);
                    }
                }.visit(document, ctx));
            }
        });
    }
}
