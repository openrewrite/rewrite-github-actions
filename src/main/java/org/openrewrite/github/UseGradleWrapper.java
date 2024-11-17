package org.openrewrite.github;

import org.openrewrite.*;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class UseGradleWrapper extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use Gradle Wrapper instead of Gradle binary directly";
    }

    @Override
    public String getDescription() {
        return "Replace calls to `gradle` with calls to `gradlew` (Gradle Wrapper) in any `.github/workflows/*.yml` file.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        final JsonPathMatcher run = new JsonPathMatcher("$.jobs..run");
        final JsonPathMatcher runsOn = new JsonPathMatcher("$.jobs..runs-on");

        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml"),
                new YamlIsoVisitor<ExecutionContext>() {
                    private Optional<String> newExecutable = Optional.empty();

                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext executionContext) {
                        Yaml.Mapping.Entry ret = super.visitMappingEntry(entry, executionContext);
                        if (runsOn.matches(getCursor())) {
                            if (ret.getValue() instanceof Yaml.Scalar) {
                                String runsOn = ((Yaml.Scalar) ret.getValue()).getValue();
                                if (runsOn.startsWith("ubuntu-") || runsOn.startsWith("macos-")) {
                                    newExecutable = Optional.of("./gradlew ");
                                } else if (runsOn.startsWith("windows-")) {
                                    newExecutable = Optional.of("gradlew ");
                                }
                            }
                        }
                        if (newExecutable.isPresent() && run.matches(getCursor())) {
                            if (ret.getValue() instanceof Yaml.Scalar) {
                                Yaml.Scalar scalar = (Yaml.Scalar) ret.getValue();
                                String replaced = Arrays.stream(scalar.getValue().split("\n"))
                                        .map(line -> {
                                            if (line.startsWith("gradle ")) {
                                                return line.replaceAll("^gradle ", newExecutable.get());
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
