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
import org.openrewrite.semver.Semver;
import org.openrewrite.yaml.AppendToSequenceVisitor;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.trait.BlockScalar;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openrewrite.semver.Semver.Ecosystem.MAVEN;
import static org.openrewrite.semver.Semver.Ecosystem.NODE;

@EqualsAndHashCode(callSuper = false)
@Value
public class SetupPythonUpgradePythonVersion extends Recipe {

    private static final JsonPathMatcher PYTHON_VERSION = new JsonPathMatcher("..steps[?(@.uses =~ 'actions/setup-python@v*.*')].with.python-version");
    private static final JsonPathMatcher JOB = new JsonPathMatcher("$.jobs.*");
    private static final Pattern MATRIX_REFERENCE = Pattern.compile("\\$\\{\\{\\s*matrix\\.([a-zA-Z_][a-zA-Z0-9_-]*)\\s*}}");
    private static final Pattern MAJOR_MINOR = Pattern.compile("[0-9]+\\.[0-9]+");

    // Concrete versions only; `Semver.isVersion` also accepts ranges such as `3.x` and `3.7 - 3.9`
    private static final Pattern CPYTHON_VERSION = Pattern.compile("[0-9]+\\.[0-9]+(\\.[0-9]+)?([-+].*)?");
    private static final Pattern NON_UPPER_BOUND_RANGE_VERSION = Pattern.compile("(?:^|[\\s,])(?:>=|>|[~^])?\\s*[v=]?([0-9]+(?:\\.[0-9]+){0,2})");

    private static final String ABOVE_ANY_PYTHON_VERSION = "999.999.999";

    @Option(displayName = "Python version",
            description = "The target Python version.",
            example = "3.14",
            required = true)
    String version;

    String displayName = "Upgrade `actions/setup-python` `python-version`";

    String description = "Update the Python version used by `actions/setup-python` if it is below the expected version number. " +
            "A `strategy.matrix` axis that feeds `python-version` gets the expected version added when none of its values reach it.";

    Set<String> tags = new LinkedHashSet<>(Arrays.asList("github", "python", "deprecation"));

    @Override
    public Validated<Object> validate() {
        return super.validate()
                .and(Validated.test("version", "must be a major.minor Python version", version,
                        v -> v != null && MAJOR_MINOR.matcher(v).matches()));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsFile(), new YamlVisitor<ExecutionContext>() {
            @Override
            public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                if (!"python-version".equals(entry.getKey().getValue()) ||
                        !(entry.getValue() instanceof Yaml.Scalar) ||
                        !PYTHON_VERSION.matches(getCursor()) ||
                        hasPythonVersionFile()) {
                    return super.visitMappingEntry(entry, ctx);
                }

                Yaml.Scalar currentValue = (Yaml.Scalar) entry.getValue();
                Matcher matrixReference = MATRIX_REFERENCE.matcher(currentValue.getValue());
                if (matrixReference.matches()) {
                    addTargetToMatrixAxis(matrixReference.group(1));
                    return super.visitMappingEntry(entry, ctx);
                }
                // A block scalar's raw value carries the block envelope, so its body is read and written through the trait
                Optional<BlockScalar> blockScalar = new BlockScalar.Matcher().get(currentValue, getCursor());
                if (blockScalar.isPresent()) {
                    BlockScalar block = blockScalar.get();
                    String body = block.getBody();
                    // Several versions in one block is a deliberate matrix, rather than a single version to raise
                    if (!body.contains("\n") && isBelowTarget(body)) {
                        return super.visitMappingEntry(entry.withValue(block.withBody(version)), ctx);
                    }
                } else if (isBelowTarget(currentValue.getValue())) {
                    return super.visitMappingEntry(entry.withValue(currentValue.withValue(version)), ctx);
                }

                return super.visitMappingEntry(entry, ctx);
            }

            private void addTargetToMatrixAxis(String axis) {
                for (Cursor c = getCursor(); c != null; c = c.getParent()) {
                    if (c.getValue() instanceof Yaml.Mapping.Entry && JOB.matches(c)) {
                        String job = ((Yaml.Mapping.Entry) c.getValue()).getKey().getValue();
                        JsonPathMatcher axisValues = new JsonPathMatcher("$.jobs." + job + ".strategy.matrix." + axis);
                        doAfterVisit(new AppendToSequenceVisitor(axisValues, version, null, false) {
                            @Override
                            protected boolean checkExistingSequenceValues(Yaml.Sequence seq, Cursor cursor) {
                                return !matrixAxisReachesTarget(seq, cursor.getParentOrThrow().getValue(), axis);
                            }
                        });
                        return;
                    }
                }
            }

            private boolean hasPythonVersionFile() {
                return value(getCursor().getParentOrThrow().getValue(), "python-version-file") != null;
            }
        });
    }

    private boolean matrixAxisReachesTarget(Yaml.Sequence values, Yaml.Mapping matrix, String axis) {
        if (values.getEntries().stream().anyMatch(e -> reachesTarget(e.getBlock()))) {
            return true;
        }
        // An `include` combination may already supply the target version for this axis, and an expression may supply anything
        Yaml.Block include = value(matrix, "include");
        if (!(include instanceof Yaml.Sequence)) {
            return include != null;
        }
        return ((Yaml.Sequence) include).getEntries().stream().anyMatch(combination ->
                combination.getBlock() instanceof Yaml.Mapping &&
                        reachesTarget(value((Yaml.Mapping) combination.getBlock(), axis)));
    }

    private static Yaml.@Nullable Block value(Yaml.Mapping mapping, String key) {
        return mapping.getEntries().stream().filter(e -> key.equals(e.getKey().getValue()))
                .map(Yaml.Mapping.Entry::getValue).findFirst().orElse(null);
    }

    private boolean reachesTarget(Yaml.@Nullable Block candidate) {
        if (!(candidate instanceof Yaml.Scalar)) {
            return false;
        }
        String value = ((Yaml.Scalar) candidate).getValue();
        return (CPYTHON_VERSION.matcher(value).matches() || Semver.validate(value, null, NODE).isValid()) &&
                !isBelowTarget(value);
    }

    private boolean isBelowTarget(String currentVersion) {
        // Maven precedence orders `major.minor`, which is not strict SemVer; ranges follow the npm grammar `setup-python` documents
        if (CPYTHON_VERSION.matcher(currentVersion).matches()) {
            return Semver.compare(currentVersion, version, MAVEN) < 0;
        }
        // A union or any lower/base version at or above the target could select a newer Python and must not be downgraded
        if (currentVersion.contains("||")) {
            return false;
        }
        Matcher rangeVersion = NON_UPPER_BOUND_RANGE_VERSION.matcher(currentVersion);
        while (rangeVersion.find()) {
            if (Semver.compare(rangeVersion.group(1), version, MAVEN) >= 0) {
                return false;
            }
        }
        // No comparator exposes a range's upper bound, so a range is only raised when it admits neither the target nor an implausibly high version
        return Semver.validate(currentVersion, null, NODE).isValid() &&
                !Semver.satisfies(version + ".0", currentVersion, NODE) &&
                !Semver.satisfies(ABOVE_ANY_PYTHON_VERSION, currentVersion, NODE);
    }
}
