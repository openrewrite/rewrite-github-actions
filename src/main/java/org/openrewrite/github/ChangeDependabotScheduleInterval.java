/*
 * Copyright 2021 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashSet;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeDependabotScheduleInterval extends Recipe {
    @Option(displayName = "Package ecosystem",
            description = "The package-ecosystem to make updates on.",
            example = "maven")
    String packageEcosystem;

    @Option(displayName = "Schedule interval",
            description = "The schedule interval value the package-ecosystem should use.",
            valid = {"daily", "weekly", "monthly"},
            example = "weekly")
    String interval;

    @Override
    public String getDisplayName() {
        return "Change dependabot schedule interval";
    }

    @Override
    public String getDescription() {
        return "Change the schedule interval for a given package-ecosystem in a `dependabot.yml` configuration file. " +
                "[The available configuration options for dependabot are listed on GitHub](https://docs.github.com/en/code-security/supply-chain-security/keeping-your-dependencies-updated-automatically/configuration-options-for-dependency-updates).";
    }

    @Override
    public Set<String> getTags() {
        Set<String> tags = new HashSet<>();
        tags.add("dependabot");
        tags.add("dependencies");
        tags.add("github");
        return tags;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/dependabot.yml"), new YamlIsoVisitor<ExecutionContext>() {
            private final JsonPathMatcher targetEcosystem = new JsonPathMatcher("$.updates[?(@.package-ecosystem =~ '" + packageEcosystem + "')].schedule.interval");

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                if (targetEcosystem.matches(getCursor()) && !((Yaml.Scalar) entry.getValue()).getValue().equals(interval)) {
                    return super.visitMappingEntry(entry.withValue(((Yaml.Scalar) entry.getValue()).withValue(interval)), ctx);
                }
                return super.visitMappingEntry(entry, ctx);
            }
        });
    }

}
