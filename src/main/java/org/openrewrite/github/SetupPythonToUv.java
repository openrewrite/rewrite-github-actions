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

import java.time.Duration;
import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class SetupPythonToUv extends Recipe {

    @Option(displayName = "UV version",
            description = "The version of the `astral-sh/setup-uv` action to use. Defaults to `v6`.",
            example = "v6",
            required = false)
    @Nullable
    String uvVersion;

    @Option(displayName = "Sync strategy",
            description = "Strategy for the `uv sync` command replacement.",
            example = "locked",
            valid = {"basic", "locked", "full"},
            required = false)
    @Nullable
    String syncStrategy;

    @Option(displayName = "Transform pip commands",
            description = "Whether to transform `pip install` commands to `uv` equivalents:\n" +
                         "- `pip install -r requirements.txt` → `uv sync`\n" +
                         "- `pip install .` → `uv sync`\n" +
                         "- `python -m pytest` → `uv run pytest`\n\n" +
                         "When disabled, only the action itself is replaced. Defaults to `true`.",
            example = "true",
            required = false)
    @Nullable
    Boolean transformPipCommands;

    @Option(displayName = "Enable cache",
            description = "Whether to automatically convert `cache: 'pip'` to `enable-cache: 'true'` " +
                         "for UV's built-in caching. When disabled, cache settings are left unchanged. " +
                         "Defaults to `true`.",
            example = "true",
            required = false)
    @Nullable
    Boolean enableCache;

    String displayName = "Replace `actions/setup-python` with `astral-sh/setup-uv`";

    String description = "Replace `actions/setup-python` action with `astral-sh/setup-uv` action for faster Python " +
               "environment setup and dependency management.\n\n" +
               "**Benefits of UV:**\n" +
               " - Significantly faster package installation and environment setup\n" +
               " - Built-in dependency resolution and locking\n" +
               " - Integrated caching for improved CI performance\n" +
               " - Drop-in replacement for pip workflows\n\n" +
               "**Transformations applied:**\n" +
               " - `actions/setup-python@v5` → `astral-sh/setup-uv@v6`\n" +
               " - `cache: 'pip'` → `enable-cache: 'true'`\n" +
               " - `pip install -r requirements.txt` → `uv sync` (configurable strategy)\n" +
               " - `python -m <module>` → `uv run <module>`\n" +
               " - Removes unnecessary `pip install --upgrade pip` steps\n\n" +
               "**Sync strategies:**\n" +
               " - `basic`: Basic synchronization (`uv sync`)\n" +
               " - `locked`: Use locked dependencies (`uv sync --locked`)\n" +
               " - `full`: Install all extras and dev dependencies (`uv sync --all-extras --dev`)\n\n" +
               "See the [UV GitHub integration guide](https://docs.astral.sh/uv/guides/integration/github/) for more details.";

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new SetupPythonToUvVisitor(
                    uvVersion != null ? uvVersion : "v6",
                    mapSyncStrategy(syncStrategy != null ? syncStrategy : "basic"),
                    transformPipCommands != null ? transformPipCommands : true,
                    enableCache != null ? enableCache : true
                ));
    }

    private static String mapSyncStrategy(String strategy) {
        switch (strategy) {
            case "basic":
                return "sync";
            case "locked":
                return "sync --locked";
            case "full":
                return "sync --all-extras --dev";
            default:
                return "sync";
        }
    }

    private static class SetupPythonToUvVisitor extends YamlIsoVisitor<ExecutionContext> {

        private static final Pattern SETUP_PYTHON_PATTERN = Pattern.compile("^actions/setup-python(@.*)?$");
        private static final Pattern PIP_INSTALL_REQUIREMENTS = Pattern.compile("^pip install -r requirements\\.txt$");
        private static final Pattern PIP_INSTALL_DEV = Pattern.compile("^pip install \\.$");
        private static final Pattern PIP_INSTALL_EDITABLE = Pattern.compile("^pip install -e \\.$");
        private static final Pattern PIP_UPGRADE = Pattern.compile("^python -m pip install --upgrade pip$");
        private static final Pattern PYTHON_MODULE_PATTERN = Pattern.compile("^python -m (.+)$");

        private final String uvVersion;
        private final String syncStrategy;
        private final boolean transformPipCommands;
        private final boolean enableCache;

        public SetupPythonToUvVisitor(String uvVersion, String syncStrategy, boolean transformPipCommands, boolean enableCache) {
            this.uvVersion = uvVersion;
            this.syncStrategy = syncStrategy;
            this.transformPipCommands = transformPipCommands;
            this.enableCache = enableCache;
        }

        @Override
        public  Yaml.Mapping.@Nullable Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            if ("uses".equals(entry.getKey().getValue()) &&
                entry.getValue() instanceof Yaml.Scalar &&
                SETUP_PYTHON_PATTERN.matcher(((Yaml.Scalar) entry.getValue()).getValue()).matches()) {

                return entry.withValue(((Yaml.Scalar) entry.getValue()).withValue("astral-sh/setup-uv@" + uvVersion));
            }

            if (enableCache && "cache".equals(entry.getKey().getValue()) &&
                entry.getValue() instanceof Yaml.Scalar &&
                "pip".equals(((Yaml.Scalar) entry.getValue()).getValue())) {

                return entry.withKey(((Yaml.Scalar) entry.getKey()).withValue("enable-cache"))
                           .withValue(((Yaml.Scalar) entry.getValue()).withValue("true"));
            }

            if ("cache-dependency-path".equals(entry.getKey().getValue())) {
                // Only remove cache-dependency-path if we're in a setup-python action
                // The parent mapping contains the 'with' block, so we need to go up one more level
                Cursor withCursor = getCursor().getParentOrThrow();  // The 'with' mapping
                Cursor stepCursor = withCursor.getParentOrThrow();   // The step mapping entry
                Cursor stepMappingCursor = stepCursor.getParentOrThrow(); // The step mapping

                if (stepMappingCursor.getValue() instanceof Yaml.Mapping) {
                    Yaml.Mapping stepMapping = (Yaml.Mapping) stepMappingCursor.getValue();

                    boolean hasSetupPython = stepMapping.getEntries().stream()
                        .anyMatch(e -> {
                            if ("uses".equals(e.getKey().getValue()) && e.getValue() instanceof Yaml.Scalar) {
                                String value = ((Yaml.Scalar) e.getValue()).getValue();
                                return value.startsWith("actions/setup-python");
                            }
                            return false;
                        });

                    if (hasSetupPython) {
                        return null;
                    }
                }
            }

            if (transformPipCommands && "run".equals(entry.getKey().getValue()) && entry.getValue() instanceof Yaml.Scalar) {
                String runCommand = ((Yaml.Scalar) entry.getValue()).getValue();

                if (PIP_UPGRADE.matcher(runCommand).matches()) {
                    return null;
                }

                if (PIP_INSTALL_REQUIREMENTS.matcher(runCommand).matches()) {
                    return entry.withValue(((Yaml.Scalar) entry.getValue()).withValue("uv " + syncStrategy));
                }

                if (PIP_INSTALL_DEV.matcher(runCommand).matches() || PIP_INSTALL_EDITABLE.matcher(runCommand).matches()) {
                    return entry.withValue(((Yaml.Scalar) entry.getValue()).withValue("uv " + syncStrategy));
                }

                if (PYTHON_MODULE_PATTERN.matcher(runCommand).matches()) {
                    String module = PYTHON_MODULE_PATTERN.matcher(runCommand).replaceFirst("$1");
                    return entry.withValue(((Yaml.Scalar) entry.getValue()).withValue("uv run " + module));
                }
            }

            return super.visitMappingEntry(entry, ctx);
        }

        @Override
        public  Yaml.Sequence.@Nullable Entry visitSequenceEntry(Yaml.Sequence.Entry entry, ExecutionContext ctx) {
            if (transformPipCommands && entry.getBlock() instanceof Yaml.Mapping) {
                Yaml.Mapping mapping = (Yaml.Mapping) entry.getBlock();

                for (Yaml.Mapping.Entry mappingEntry : mapping.getEntries()) {
                    if ("run".equals(mappingEntry.getKey().getValue()) &&
                        mappingEntry.getValue() instanceof Yaml.Scalar) {

                        String runCommand = ((Yaml.Scalar) mappingEntry.getValue()).getValue();

                        if (PIP_UPGRADE.matcher(runCommand).matches()) {
                            return null;
                        }
                    }
                }
            }

            return super.visitSequenceEntry(entry, ctx);
        }
    }
}
