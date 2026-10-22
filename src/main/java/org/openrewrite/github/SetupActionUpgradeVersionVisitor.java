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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.semver.Semver;
import org.openrewrite.yaml.AppendToSequenceVisitor;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.trait.BlockScalar;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openrewrite.semver.Semver.Ecosystem.MAVEN;
import static org.openrewrite.semver.Semver.Ecosystem.NODE;

class SetupActionUpgradeVersionVisitor extends YamlVisitor<ExecutionContext> {

    static final Pattern MAJOR_MINOR = Pattern.compile("[0-9]+\\.[0-9]+");

    private static final JsonPathMatcher JOB = new JsonPathMatcher("$.jobs.*");
    private static final Pattern MATRIX_REFERENCE = Pattern.compile("\\$\\{\\{\\s*matrix\\.([a-zA-Z_][a-zA-Z0-9_-]*)\\s*}}");

    // Concrete versions only; `Semver.isVersion` also accepts ranges such as `3.x` and `3.7 - 3.9`
    private static final Pattern CONCRETE_VERSION = Pattern.compile("[0-9]+\\.[0-9]+(\\.[0-9]+)?([-+].*)?");
    private static final Pattern NON_UPPER_BOUND_RANGE_VERSION = Pattern.compile("(?:^|[\\s,])(?:>=|>|[~^])?\\s*[v=]?([0-9]+(?:\\.[0-9]+){0,2})");

    private static final String ABOVE_ANY_VERSION = "999.999.999";

    private final String versionInput;
    private final String versionFileInput;
    private final JsonPathMatcher versionInputMatcher;
    private final Collection<String> latestVersionAliases;
    private final String version;

    SetupActionUpgradeVersionVisitor(String action, String versionInput, String versionFileInput,
                                     Collection<String> latestVersionAliases, String version) {
        this.versionInput = versionInput;
        this.versionFileInput = versionFileInput;
        this.versionInputMatcher = new JsonPathMatcher("..steps[?(@.uses =~ '" + action + "@v*.*')].with." + versionInput);
        this.latestVersionAliases = latestVersionAliases;
        this.version = version;
    }

    @Override
    public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
        if (!versionInput.equals(entry.getKey().getValue()) ||
                !(entry.getValue() instanceof Yaml.Scalar) ||
                !versionInputMatcher.matches(getCursor()) ||
                hasVersionFile()) {
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

    private boolean hasVersionFile() {
        return value(getCursor().getParentOrThrow().getValue(), versionFileInput) != null;
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
        if (latestVersionAliases.contains(value)) {
            return true;
        }
        return (CONCRETE_VERSION.matcher(value).matches() || Semver.validate(value, null, NODE).isValid()) &&
                !isBelowTarget(value);
    }

    private boolean isBelowTarget(String currentVersion) {
        // Maven precedence orders `major.minor`, which is not strict SemVer
        if (CONCRETE_VERSION.matcher(currentVersion).matches()) {
            return Semver.compare(currentVersion, version, MAVEN) < 0;
        }
        // A union or any lower/base version at or above the target could select a newer version and must not be downgraded
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
                !Semver.satisfies(ABOVE_ANY_VERSION, currentVersion, NODE);
    }
}
