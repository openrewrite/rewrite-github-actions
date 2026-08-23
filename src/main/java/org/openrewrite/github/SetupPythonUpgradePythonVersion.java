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

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.semver.Semver;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class SetupPythonUpgradePythonVersion extends Recipe {

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
                .and(Validated.required("version", version))
                .and(Validated.test("version", "must be a major.minor Python version", version,
                        version -> version != null && parseTargetVersion(version) != null));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        PythonVersion parsedVersion = parseTargetVersion(version);
        if (parsedVersion == null) {
            return TreeVisitor.noop();
        }
        return Preconditions.check(new IsGitHubActionsWorkflow(), new UpgradePythonVersionVisitor(version, parsedVersion, toSemverVersion(version)));
    }

    @AllArgsConstructor
    private static class UpgradePythonVersionVisitor extends YamlVisitor<ExecutionContext> {
        private static final JsonPathMatcher pythonVersion = new JsonPathMatcher("..steps[?(@.uses =~ 'actions/setup-python@v*.*')].with.python-version");
        private static final Pattern pythonVersionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)(?:\\.[0-9]+)?(?:[-+][0-9A-Za-z.-]+)?");
        private static final Pattern targetPythonVersionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)");
        private static final Pattern lowerBoundPattern = Pattern.compile("(?:^|[\\s,])(?:>=|>)\\s*([0-9]+)\\.([0-9]+)(?:\\.[0-9]+)*(?:[-+][^\\s,]+)?");
        private static final Pattern upperBoundPattern = Pattern.compile("(?:^|[\\s,])<=?\\s*([0-9]+)\\.([0-9]+)(?:\\.[0-9]+)*(?:[-+][^\\s,]+)?");
        private static final Pattern hyphenRangePattern = Pattern.compile("(?:^|\\s)[0-9]+\\.[0-9]+(?:\\.[0-9]+)*\\s+-\\s+([0-9]+)\\.([0-9]+)(?:\\.[0-9]+)*(?:\\s|$)");
        private static final Pattern xRangePattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.(?:x|X|\\*)");
        private static final Pattern majorXRangePattern = Pattern.compile("([0-9]+)\\.(?:x|X|\\*)");
        private static final Pattern tildeRangePattern = Pattern.compile("~\\s*([0-9]+)\\.([0-9]+)(?:\\.[0-9]+)?");

        private final String version;
        private final PythonVersion parsedVersion;
        private final String semverVersion;

        @Override
        public Yaml visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            if (!pythonVersion.matches(getCursor()) || hasPythonVersionFile()) {
                return super.visitMappingEntry(entry, ctx);
            }

            if (!(entry.getValue() instanceof Yaml.Scalar)) {
                return super.visitMappingEntry(entry, ctx);
            }

            Yaml.Scalar currentValue = (Yaml.Scalar) entry.getValue();
            if (!isSafelyBelowTarget(currentValue.getValue(), parsedVersion, semverVersion)) {
                return super.visitMappingEntry(entry, ctx);
            }

            return super.visitMappingEntry(
                    entry.withValue(currentValue.withValue(version)),
                    ctx
            );
        }

        private boolean hasPythonVersionFile() {
            Object parent = getCursor().getParentOrThrow().getValue();
            if (!(parent instanceof Yaml.Mapping)) {
                return false;
            }
            for (Yaml.Mapping.Entry entry : ((Yaml.Mapping) parent).getEntries()) {
                if ("python-version-file".equals(entry.getKey().getValue())) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isSafelyBelowTarget(String currentVersion, PythonVersion targetVersion, String semverVersion) {
            PythonVersion exactVersion = parseVersion(currentVersion);
            if (exactVersion != null) {
                return exactVersion.compareTo(targetVersion) < 0;
            }

            if (currentVersion.contains("||") ||
                    Semver.validate(currentVersion, null, Semver.Ecosystem.NODE).isInvalid() ||
                    Semver.satisfies(semverVersion, currentVersion, Semver.Ecosystem.NODE)) {
                return false;
            }

            Matcher lowerBound = lowerBoundPattern.matcher(currentVersion);
            while (lowerBound.find()) {
                PythonVersion lowerBoundVersion = new PythonVersion(
                        Integer.parseInt(lowerBound.group(1)),
                        Integer.parseInt(lowerBound.group(2))
                );
                if (lowerBoundVersion.compareTo(targetVersion) >= 0) {
                    return false;
                }
            }

            Matcher xRange = xRangePattern.matcher(currentVersion);
            if (xRange.matches()) {
                PythonVersion xRangeVersion = new PythonVersion(
                        Integer.parseInt(xRange.group(1)),
                        Integer.parseInt(xRange.group(2))
                );
                return xRangeVersion.compareTo(targetVersion) < 0;
            }

            Matcher majorXRange = majorXRangePattern.matcher(currentVersion);
            if (majorXRange.matches()) {
                return Integer.parseInt(majorXRange.group(1)) < targetVersion.major;
            }

            Matcher tildeRange = tildeRangePattern.matcher(currentVersion);
            if (tildeRange.matches()) {
                PythonVersion tildeRangeVersion = new PythonVersion(
                        Integer.parseInt(tildeRange.group(1)),
                        Integer.parseInt(tildeRange.group(2))
                );
                return tildeRangeVersion.compareTo(targetVersion) < 0;
            }

            Matcher upperBound = upperBoundPattern.matcher(currentVersion);
            while (upperBound.find()) {
                PythonVersion upperBoundVersion = new PythonVersion(
                        Integer.parseInt(upperBound.group(1)),
                        Integer.parseInt(upperBound.group(2))
                );
                if (upperBoundVersion.compareTo(targetVersion) <= 0) {
                    return true;
                }
            }

            Matcher hyphenRange = hyphenRangePattern.matcher(currentVersion);
            while (hyphenRange.find()) {
                PythonVersion upperBoundVersion = new PythonVersion(
                        Integer.parseInt(hyphenRange.group(1)),
                        Integer.parseInt(hyphenRange.group(2))
                );
                if (upperBoundVersion.compareTo(targetVersion) < 0) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String toSemverVersion(String version) {
        return version + ".0";
    }

    private static @Nullable PythonVersion parseTargetVersion(String version) {
        Matcher matcher = UpgradePythonVersionVisitor.targetPythonVersionPattern.matcher(version);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new PythonVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static @Nullable PythonVersion parseVersion(String version) {
        Matcher matcher = UpgradePythonVersionVisitor.pythonVersionPattern.matcher(version);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new PythonVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @AllArgsConstructor
    private static class PythonVersion implements Comparable<PythonVersion> {
        private final int major;
        private final int minor;

        @Override
        public int compareTo(PythonVersion other) {
            int majorComparison = Integer.compare(major, other.major);
            return majorComparison == 0 ? Integer.compare(minor, other.minor) : majorComparison;
        }
    }
}
