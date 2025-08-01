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
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoveUnusedWorkflowDispatchInputs extends Recipe {

    private static final Pattern INPUT_REFERENCE_PATTERN = Pattern.compile("[$][{][{]\\s*github[.]event[.]inputs[.](\\w+)\\s*[}][}]");

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

                // Find workflow_dispatch inputs
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                        if (isWorkflowDispatchInputsEntry(e)) {
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
                }.visit(document, ctx);

                // Second pass: find all input references
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                        String value = scalar.getValue();
                        Matcher matcher = INPUT_REFERENCE_PATTERN.matcher(value);
                        while (matcher.find()) {
                            usedInputs.add(matcher.group(1));
                        }
                        return super.visitScalar(scalar, ctx);
                    }
                }.visit(document, ctx);

                // Third pass: remove unused inputs
                return (Yaml.Document) new YamlIsoVisitor<ExecutionContext>() {
                    @Nullable
                    private Yaml.Mapping currentInputsMapping;

                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        if (isWorkflowDispatchInputsEntry(entry)) {
                            Yaml.Block value = entry.getValue();
                            if (value instanceof Yaml.Mapping) {
                                currentInputsMapping = (Yaml.Mapping) value;
                                Yaml.Mapping.Entry result = super.visitMappingEntry(entry, ctx);
                                currentInputsMapping = null;
                                return result;
                            }
                        }

                        if (currentInputsMapping != null && entry.getKey() instanceof Yaml.Scalar) {
                            String inputName = (entry.getKey()).getValue();
                            if (definedInputs.contains(inputName) && !usedInputs.contains(inputName)) {
                                return null;
                            }
                        }

                        return super.visitMappingEntry(entry, ctx);
                    }
                }.visit(document, ctx);
            }

            private boolean isWorkflowDispatchInputsEntry(Yaml.Mapping.Entry entry) {
                if (!(entry.getKey() instanceof Yaml.Scalar)) {
                    return false;
                }
                String key = (entry.getKey()).getValue();
                if (!"inputs".equals(key)) {
                    return false;
                }

                // Check if this is under on.workflow_dispatch
                Cursor parent = getCursor().getParent();
                while (parent != null && parent.getValue() instanceof Yaml) {
                    if (parent.getValue() instanceof Yaml.Mapping.Entry) {
                        Yaml.Mapping.Entry parentEntry = parent.getValue();
                        if (parentEntry.getKey() instanceof Yaml.Scalar) {
                            String parentKey = ((Yaml.Scalar) parentEntry.getKey()).getValue();
                            if ("workflow_dispatch".equals(parentKey)) {
                                // Check if grandparent is "on"
                                Cursor grandParent = parent.getParent();
                                if (grandParent != null && grandParent.getValue() instanceof Yaml.Mapping) {
                                    grandParent = grandParent.getParent();
                                    if (grandParent != null && grandParent.getValue() instanceof Yaml.Mapping.Entry) {
                                        Yaml.Mapping.Entry grandParentEntry = grandParent.getValue();
                                        if (grandParentEntry.getKey() instanceof Yaml.Scalar) {
                                            String grandParentKey = ((Yaml.Scalar) grandParentEntry.getKey()).getValue();
                                            return "on".equals(grandParentKey);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    parent = parent.getParent();
                }
                return false;
            }
        });
    }
}
