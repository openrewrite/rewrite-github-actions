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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.yaml.ChangeValue;

@Value
@EqualsAndHashCode(callSuper = true)
public class ChangeActionVersion extends Recipe {
    @Option(displayName = "Action",
            description = "Name of the action to update.",
            example = "actions/setup-java")
    String action;

    @Option(displayName = "Version",
            description = "Version to use.",
            example = "v4")
    String version;

    @Override
    public String getDisplayName() {
        return "Change GitHub Action version";
    }

    @Override
    public String getDescription() {
        return "Change the version of a GitHub Action in any `.github/workflows/*.yml` file.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml"),
                new ChangeValue(
                        "$.jobs..steps[?(@.uses =~ '" + action + "(?:@.+)?')].uses",
                        action + '@' + version).getVisitor());
    }
}
