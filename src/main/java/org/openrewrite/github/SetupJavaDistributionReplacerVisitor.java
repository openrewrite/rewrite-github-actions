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

import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;

@RequiredArgsConstructor
class SetupJavaDistributionReplacerVisitor extends YamlIsoVisitor<ExecutionContext> {

    static final JsonPathMatcher DISTRIBUTION_MATCHER = new JsonPathMatcher("..steps[?(@.uses =~ 'actions/setup-java@v[23].*')].with.distribution");

    private final List<String> originalDistributions;
    private final String newDistribution;

    @Override
    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
        if (DISTRIBUTION_MATCHER.matches(getCursor()) && this.originalDistributions.contains(((Yaml.Scalar) entry.getValue()).getValue())) {
            return super.visitMappingEntry(entry.withValue(((Yaml.Scalar) entry.getValue()).withValue(this.newDistribution)), ctx);
        }
        return super.visitMappingEntry(entry, ctx);
    }
}
