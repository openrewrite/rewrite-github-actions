/*
 * Copyright 2024 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

// We don't use the atomicity of AtomicBoolean, just the mutability
public class UseGradleWrapper extends ScanningRecipe<UseGradleWrapperAccumulator> {
    @Override
    public String getDisplayName() {
        return "Use Gradle Wrapper instead of Gradle binary directly";
    }

    @Override
    public String getDescription() {
        return "Replace calls to `gradle` with calls to `gradlew` (Gradle Wrapper) in any `.github/workflows/*.yml` file.";
    }

    @Override
    public UseGradleWrapperAccumulator getInitialValue(ExecutionContext ctx) {
        return new UseGradleWrapperAccumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(UseGradleWrapperAccumulator acc) {
        return new YamlIsoVisitor<ExecutionContext>() {
            final JsonPathMatcher runsOn = new JsonPathMatcher("$.jobs..runs-on");

            @Override
            public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                Yaml.Document ret = super.visitDocument(document, ctx);
                // TODO fix the check for gradlew not to use file operations
                acc.gradlewExists = Paths.get("gradlew").toFile().exists();
                return ret;
            }

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry ret = super.visitMappingEntry(entry, ctx);
                if (runsOn.matches(getCursor())) {
                    if (ret.getValue() instanceof Yaml.Scalar) {
                        String runsOn = ((Yaml.Scalar) ret.getValue()).getValue();
                        if (runsOn.startsWith("ubuntu-") || runsOn.startsWith("macos-")) {
                            acc.newExecutableName = Optional.of("./gradlew ");
                        } else if (runsOn.startsWith("windows-")) {
                            acc.newExecutableName = Optional.of("gradlew ");
                        }
                    }
                }
                return ret;
            }
        } ;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(UseGradleWrapperAccumulator acc) {
        boolean wrapperExists = acc.gradlewExists != null && acc.gradlewExists;
        boolean newExecutableDecided = acc.newExecutableName.isPresent();
        return (wrapperExists && newExecutableDecided) ? changingVisitor(acc) : super.getVisitor(acc);
    }

    private TreeVisitor<?, ExecutionContext> changingVisitor(UseGradleWrapperAccumulator acc) {

        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml"),
                new YamlIsoVisitor<ExecutionContext>() {
                    final JsonPathMatcher run = new JsonPathMatcher("$.jobs..run");

                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        Yaml.Mapping.Entry ret = super.visitMappingEntry(entry, ctx);
                        if (run.matches(getCursor())) {
                            if (ret.getValue() instanceof Yaml.Scalar) {
                                Yaml.Scalar scalar = (Yaml.Scalar) ret.getValue();
                                String replaced = Arrays.stream(scalar.getValue().split("\n"))
                                        .map(line -> {
                                            if (line.startsWith("gradle ")) {
                                                return line.replaceAll("^gradle ", acc.newExecutableName.get());
                                            } else {
                                                return line;
                                            }
                                        }).collect(Collectors.joining("\n"));
                                boolean changed = ! replaced.equals(scalar.getValue());
                                if (changed) {
                                    return ret.withValue(scalar.withValue(replaced));
                                } else {
                                    return ret;
                                }
                            }
                        }
                        return ret;
                    }
                }
                );
    }
}
