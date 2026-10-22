/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.*;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Collections.singletonList;
import static org.openrewrite.github.SetupActionUpgradeVersionVisitor.MAJOR_MINOR;

@EqualsAndHashCode(callSuper = false)
@Value
public class SetupGoUpgradeGoVersion extends Recipe {

    @Option(displayName = "Go version",
            description = "The target Go version.",
            example = "1.26",
            required = true)
    String version;

    String displayName = "Upgrade `actions/setup-go` `go-version`";

    String description = "Update the Go version used by `actions/setup-go` if it is below the expected version number. " +
            "A `strategy.matrix` axis that feeds `go-version` gets the expected version added when none of its values reach it.";

    Set<String> tags = new LinkedHashSet<>(Arrays.asList("github", "go", "deprecation"));

    @Override
    public Validated<Object> validate() {
        return super.validate()
                .and(Validated.test("version", "must be a major.minor Go version", version,
                        v -> v != null && MAJOR_MINOR.matcher(v).matches()));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsFile(),
                new SetupActionUpgradeVersionVisitor("actions/setup-go", "go-version", "go-version-file",
                        singletonList("stable"), version));
    }
}
