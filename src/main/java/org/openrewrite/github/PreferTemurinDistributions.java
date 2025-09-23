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
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.openrewrite.github.SetupJavaDistributionReplacerVisitor.DISTRIBUTION_MATCHER;

public class PreferTemurinDistributions extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use `actions/setup-java` `temurin` distribution as they are cached in hosted runners";
    }

    @Override
    public String getDescription() {
        return "[Host runners](https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners#supported-runners-and-hardware-resources/) include Temurin by default as part of the [hosted tool cache](https://github.com/actions/setup-java/blob/main/docs/advanced-usage.md#hosted-tool-cache). " +
                "Using Temurin speeds up builds as there is no need to download and configure the Java SDK with every build.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"), new UseTemurinVisitor());
    }

    private static final Pattern pattern = Pattern.compile("^(windows|ubuntu|macos)-(latest|\\d+(\\.\\d+)?)$");

    private static class UseTemurinVisitor extends YamlIsoVisitor<ExecutionContext> {

        private List<String> runsOn = new ArrayList<>();

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            if ("runs-on".equals(entry.getKey().getValue())) {
                runsOn = new ArrayList<>();
                if (entry.getValue() instanceof Yaml.Sequence) {
                    Yaml.Sequence sequence = (Yaml.Sequence) entry.getValue();
                    for (Yaml.Sequence.Entry e : sequence.getEntries()) {
                        runsOn.add(((Yaml.Scalar) e.getBlock()).getValue());
                    }
                } else if (entry.getValue() instanceof Yaml.Scalar) {
                    runsOn.add(((Yaml.Scalar) entry.getValue()).getValue());
                }
                return super.visitMappingEntry(entry, ctx);
            }

            int hostedRunnersCount = Math.toIntExact(runsOn.stream().filter(e -> pattern.matcher(e).matches()).count());
            if (hostedRunnersCount == runsOn.size() && DISTRIBUTION_MATCHER.matches(getCursor()) && !"temurin".equals(((Yaml.Scalar) entry.getValue()).getValue())) {
                return super.visitMappingEntry(entry.withValue(((Yaml.Scalar) entry.getValue()).withValue("temurin")), ctx);
            }
            return super.visitMappingEntry(entry, ctx);
        }
    }
}
