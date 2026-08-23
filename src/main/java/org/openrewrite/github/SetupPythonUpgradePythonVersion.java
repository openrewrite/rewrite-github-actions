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
import org.openrewrite.*;
import org.openrewrite.semver.Semver;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.openrewrite.semver.Semver.Ecosystem.MAVEN;
import static org.openrewrite.semver.Semver.Ecosystem.NODE;

@EqualsAndHashCode(callSuper = false)
@Value
public class SetupPythonUpgradePythonVersion extends Recipe {

    private static final JsonPathMatcher PYTHON_VERSION = new JsonPathMatcher("..steps[?(@.uses =~ 'actions/setup-python@v*.*')].with.python-version");
    private static final Pattern MAJOR_MINOR = Pattern.compile("[0-9]+\\.[0-9]+");

    // Concrete versions only; `Semver.isVersion` also accepts ranges such as `3.x` and `3.7 - 3.9`
    private static final Pattern CPYTHON_VERSION = Pattern.compile("[0-9]+\\.[0-9]+(\\.[0-9]+)?([-+].*)?");

    private static final String ABOVE_ANY_PYTHON_VERSION = "999.999.999";

    @Option(displayName = "Python version",
            description = "The target Python version.",
            example = "3.14",
            required = true)
    String version;

    String displayName = "Upgrade `actions/setup-python` `python-version`";

    String description = "Update the Python version used by `actions/setup-python` if it is below the expected version number.";

    Set<String> tags = new LinkedHashSet<>(Arrays.asList("github", "python", "deprecation"));

    @Override
    public Validated<Object> validate() {
        return super.validate()
                .and(Validated.test("version", "must be a major.minor Python version", version,
                        v -> v != null && MAJOR_MINOR.matcher(v).matches()));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsWorkflow(), new YamlVisitor<ExecutionContext>() {
            @Override
            public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                if (!"python-version".equals(entry.getKey().getValue()) ||
                        !(entry.getValue() instanceof Yaml.Scalar) ||
                        !PYTHON_VERSION.matches(getCursor()) ||
                        hasPythonVersionFile()) {
                    return super.visitMappingEntry(entry, ctx);
                }

                Yaml.Scalar currentValue = (Yaml.Scalar) entry.getValue();
                // The value of a block scalar carries the block envelope, which `withValue` would clobber
                if (currentValue.getStyle() == Yaml.Scalar.Style.LITERAL ||
                        currentValue.getStyle() == Yaml.Scalar.Style.FOLDED) {
                    return super.visitMappingEntry(entry, ctx);
                }

                if (!isBelowTarget(currentValue.getValue())) {
                    return super.visitMappingEntry(entry, ctx);
                }

                return super.visitMappingEntry(entry.withValue(currentValue.withValue(version)), ctx);
            }

            private boolean hasPythonVersionFile() {
                Yaml.Mapping with = getCursor().getParentOrThrow().getValue();
                return with.getEntries().stream()
                        .anyMatch(e -> "python-version-file".equals(e.getKey().getValue()));
            }
        });
    }

    private boolean isBelowTarget(String currentVersion) {
        // Maven precedence orders `major.minor`, which is not strict SemVer; ranges follow the npm grammar `setup-python` documents
        if (CPYTHON_VERSION.matcher(currentVersion).matches()) {
            return Semver.compare(currentVersion, version, MAVEN) < 0;
        }
        // No comparator exposes a range's upper bound, so a range is only raised when it admits neither the target nor an implausibly high version
        return Semver.validate(currentVersion, null, NODE).isValid() &&
                !Semver.satisfies(version + ".0", currentVersion, NODE) &&
                !Semver.satisfies(ABOVE_ANY_PYTHON_VERSION, currentVersion, NODE);
    }
}
