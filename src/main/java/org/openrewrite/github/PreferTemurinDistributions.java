/*
 * Copyright 2023 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.HasSourcePath;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.openrewrite.github.ActionsSetupJavaAdoptOpenJDKToTemurin.DISTRIBUTION_MATCHER;

public class PreferTemurinDistributions extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use `actions/setup-java` `temurin` distribution as they are cached in hosted runners";
    }

    @Override
    public String getDescription() {
        return "[Host runners](https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners#supported-runners-and-hardware-resources/) include Temurin by default as part of the (hosted tool cache)(https://github.com/actions/setup-java/blob/main/docs/advanced-usage.md#hosted-tool-cache)." +
                "\n. Using Temurin speeds up builds as there is no need to download and configure the Java SDK with every build.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new HasSourcePath<>(".github/workflows/*.yml");
    }

    @Override
    protected YamlVisitor<ExecutionContext> getVisitor() {
        return new UseTemurinVisitor();
    }

    private static List<String> runsOn = new ArrayList<>();

    private static Pattern pattern = Pattern.compile("^(windows|ubuntu|macos)-(latest|\\d+(\\.\\d+)?)$");

    private static class UseTemurinVisitor extends YamlIsoVisitor<ExecutionContext> {
        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {

            if ("runs-on".equals(entry.getKey().getValue())){

                runsOn = new ArrayList<>();

                if (entry.getValue() instanceof Yaml.Sequence){

                    Yaml.Sequence sequence = ((Yaml.Sequence) entry.getValue());

                    for (Yaml.Sequence.Entry e : sequence.getEntries()){
                        runsOn.add(((Yaml.Scalar) e.getBlock()).getValue());
                    }


                }else if (entry.getValue() instanceof Yaml.Scalar){
                    runsOn.add(((Yaml.Scalar) entry.getValue()).getValue());
                }

                return super.visitMappingEntry(entry, ctx);
            }

            final int hostedRunnersCount  = Math.toIntExact(runsOn.stream().filter(e -> pattern.matcher(e).matches()).count());

            if (hostedRunnersCount == runsOn.size() && DISTRIBUTION_MATCHER.matches(getCursor()) && !"temurin".equals(((Yaml.Scalar) entry.getValue()).getValue())) {
                return super.visitMappingEntry(entry.withValue(((Yaml.Scalar) entry.getValue()).withValue("temurin")), ctx);
            }
            return super.visitMappingEntry(entry, ctx);
        }
    }

}
