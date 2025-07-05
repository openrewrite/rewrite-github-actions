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
public class ReplaceSecretKeys extends Recipe {
    @Option(displayName = "Old key name",
            description = "The name of the key to be replaced",
            example = "ossrh_username")
    String oldKeyName;

    @Option(displayName = "New key name",
            description = "The new key name to use",
            example = "sonatype_username")
    String newKeyName;

    @Option(displayName = "File matcher",
            description = "Optional file path matcher",
            required = false,
            example = ".github/workflows/*.yml")
    @Nullable
    String fileMatcher;

    @Override
    public String getDisplayName() {
        return "Replace secret key names in GitHub Actions";
    }

    @Override
    public String getDescription() {
        return "Replace key names used for secrets in GitHub Actions workflow files.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(fileMatcher != null ? fileMatcher : ".github/workflows/*.yml"), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);

                if (e.getKey() instanceof Yaml.Scalar) {
                    Yaml.Scalar key = (Yaml.Scalar) e.getKey();
                    if (oldKeyName.equals(key.getValue())) {
                        return e.withKey(key.withValue(newKeyName));
                    }
                }

                return e;
            }
        });
    }
}
