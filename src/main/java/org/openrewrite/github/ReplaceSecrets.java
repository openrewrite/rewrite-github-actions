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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceSecrets extends Recipe {
    @Option(displayName = "Old secret name",
            description = "The name of the secret to be replaced",
            example = "OSSRH_S01_USERNAME")
    String oldSecretName;

    @Option(displayName = "New secret name",
            description = "The new secret name to use",
            example = "SONATYPE_USERNAME")
    String newSecretName;

    @Option(displayName = "File matcher",
            description = "Optional file path matcher",
            required = false,
            example = ".github/workflows/*.yml")
    @Nullable
    String fileMatcher;

    @Override
    public String getDisplayName() {
        return "Replace GitHub Action secret names";
    }

    @Override
    public String getDescription() {
        return "Replace references to GitHub Action secrets in workflow files.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(fileMatcher != null ? fileMatcher : ".github/workflows/*.yml"), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                Yaml.Scalar s = super.visitScalar(scalar, ctx);
                String value = s.getValue();

                // Check if the scalar contains a secret reference with flexible whitespace
                // Pattern: ${{<whitespace>secrets.OLD_SECRET<whitespace>}}
                String regex = "\\$\\{\\{\\s*secrets\\." + oldSecretName + "\\s*}}";
                if (value.matches(".*" + regex + ".*")) {
                    String newValue = value.replaceAll(regex, "\\${{ secrets." + newSecretName + " }}");
                    return s.withValue(newValue);
                }

                return s;
            }
        });
    }
}
