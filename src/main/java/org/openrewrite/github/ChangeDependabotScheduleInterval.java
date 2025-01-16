/*
 * Copyright 2025 the original author or authors.
 *
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.github;

import org.openrewrite.*;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Value;

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
