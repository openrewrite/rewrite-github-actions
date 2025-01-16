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

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class SetupJavaAdoptOpenJDKToTemurin extends Recipe {


    @Override
    public String getDisplayName() {
        return "Use `actions/setup-java` `temurin` distribution";
    }

    @Override
    public String getDescription() {
        return "Adopt OpenJDK got moved to Eclipse Temurin and won't be updated anymore. " +
                "It is highly recommended to migrate workflows from adopt to temurin to keep receiving software and security updates. " +
                "See more details in the [Good-bye AdoptOpenJDK post](https://blog.adoptopenjdk.net/2021/08/goodbye-adoptopenjdk-hello-adoptium/).";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("security");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/workflows/*.yml"), new SetupJavaDistributionReplacerVisitor(Arrays.asList("adopt", "adopt-hotspot"), "temurin"));
    }

}
