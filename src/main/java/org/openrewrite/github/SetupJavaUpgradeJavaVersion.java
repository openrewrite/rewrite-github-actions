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

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class SetupJavaUpgradeJavaVersion extends Recipe {

    @Option(displayName = "Minimum major Java version (defaults to 21)",
            example = "21",
            required = false)
    @Nullable
    Integer minimumJavaMajorVersion;

    String displayName = "Upgrade `actions/setup-java` `java-version`";

    String description = "Update the Java version used by `actions/setup-java` if it is below the expected version number.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsFile(), new UpgradeJavaVersionVisitor(
                minimumJavaMajorVersion == null ? 21 : minimumJavaMajorVersion
        ));
    }

    @AllArgsConstructor
    private static class UpgradeJavaVersionVisitor extends YamlVisitor<ExecutionContext> {
        private static final JsonPathMatcher javaVersion = new JsonPathMatcher("..steps[?(@.uses =~ 'actions/setup-java@v*.*')].with.java-version");
        private static final Pattern javaVersionPattern = Pattern.compile("([0-9]+)(\\.[0-9]+)*([-+].*)?");

        private final int minimumJavaMajorVersion;

        private static final Pattern matrixReference = Pattern.compile("\\$\\{\\{\\s*matrix\\.([a-zA-Z_][a-zA-Z0-9_-]*)\\s*}}");
        private static final JsonPathMatcher job = new JsonPathMatcher("$.jobs.*");

        @Override
        public Yaml visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
            Yaml.Mapping m = (Yaml.Mapping) super.visitMapping(mapping, ctx);
            if (!job.matches(getCursor().getParentOrThrow())) {
                return m;
            }
            Yaml.Block steps = value(m, "steps");
            if (!(steps instanceof Yaml.Sequence)) {
                return m;
            }
            Set<String> axes = new HashSet<>();
            for (Yaml.Sequence.Entry step : ((Yaml.Sequence) steps).getEntries()) {
                if (!(step.getBlock() instanceof Yaml.Mapping)) {
                    continue;
                }
                Yaml.Mapping stepMapping = (Yaml.Mapping) step.getBlock();
                Yaml.Block uses = value(stepMapping, "uses");
                Yaml.Block with = value(stepMapping, "with");
                if (!(uses instanceof Yaml.Scalar) || !((Yaml.Scalar) uses).getValue().startsWith("actions/setup-java@") ||
                        !(with instanceof Yaml.Mapping)) {
                    continue;
                }
                Yaml.Block version = value((Yaml.Mapping) with, "java-version");
                if (version instanceof Yaml.Scalar) {
                    Matcher reference = matrixReference.matcher(((Yaml.Scalar) version).getValue());
                    if (reference.matches()) {
                        axes.add(reference.group(1));
                    }
                }
            }
            return m.withEntries(ListUtils.map(m.getEntries(), strategy -> {
                if (!"strategy".equals(strategy.getKey().getValue()) || !(strategy.getValue() instanceof Yaml.Mapping)) {
                    return strategy;
                }
                Yaml.Mapping strategyValue = (Yaml.Mapping) strategy.getValue();
                return strategy.withValue(strategyValue.withEntries(ListUtils.map(strategyValue.getEntries(), matrix -> {
                    if (!"matrix".equals(matrix.getKey().getValue()) || !(matrix.getValue() instanceof Yaml.Mapping)) {
                        return matrix;
                    }
                    Yaml.Mapping matrixValue = (Yaml.Mapping) matrix.getValue();
                    // Includes and exclusions can encode relationships between axes. Do not invalidate them.
                    if (value(matrixValue, "include") != null || value(matrixValue, "exclude") != null) {
                        return matrix;
                    }
                    return matrix.withValue(matrixValue.withEntries(ListUtils.map(matrixValue.getEntries(), axis -> {
                        if (!axes.contains(axis.getKey().getValue()) || !(axis.getValue() instanceof Yaml.Sequence)) {
                            return axis;
                        }
                        Yaml.Sequence values = (Yaml.Sequence) axis.getValue();
                        List<Yaml.Sequence.Entry> upgraded = ListUtils.map(values.getEntries(), item ->
                                item.getBlock() instanceof Yaml.Scalar ?
                                        item.withBlock(upgrade((Yaml.Scalar) item.getBlock())) : item);
                        if (upgraded == values.getEntries()) {
                            return axis;
                        }
                        Set<String> seen = new HashSet<>();
                        List<Yaml.Sequence.Entry> distinct = ListUtils.map(upgraded, item ->
                                item.getBlock() instanceof Yaml.Scalar &&
                                        !seen.add(((Yaml.Scalar) item.getBlock()).getValue()) ? null : item);
                        if (distinct.size() != upgraded.size()) {
                            String trailingComma = upgraded.get(upgraded.size() - 1).getTrailingCommaPrefix();
                            distinct = ListUtils.mapLast(distinct, item -> item.withTrailingCommaPrefix(trailingComma));
                        }
                        return axis.withValue(values.withEntries(distinct));
                    })));
                })));
            }));
        }

        private static Yaml.@Nullable Block value(Yaml.Mapping mapping, String key) {
            return mapping.getEntries().stream().filter(e -> key.equals(e.getKey().getValue()))
                    .map(Yaml.Mapping.Entry::getValue).findFirst().orElse(null);
        }

        private Yaml.Scalar upgrade(Yaml.Scalar scalar) {
            Matcher matcher = javaVersionPattern.matcher(scalar.getValue());
            if (matcher.matches()) {
                try {
                    if (Integer.parseInt(matcher.group(1)) < minimumJavaMajorVersion) {
                        return scalar.withValue(String.valueOf(minimumJavaMajorVersion));
                    }
                } catch (NumberFormatException ignored) {
                    // Leave values outside the supported integer range unchanged.
                }
            }
            return scalar;
        }

        @Override
        public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            if (!"java-version".equals(entry.getKey().getValue()) ||
                    !javaVersion.matches(getCursor())) {
                return super.visitMappingEntry(entry, ctx);
            }

            if (!(entry.getValue() instanceof Yaml.Scalar)) {
                return super.visitMappingEntry(entry, ctx);
            }
            return super.visitMappingEntry(entry.withValue(upgrade((Yaml.Scalar) entry.getValue())), ctx);
        }
    }
}
