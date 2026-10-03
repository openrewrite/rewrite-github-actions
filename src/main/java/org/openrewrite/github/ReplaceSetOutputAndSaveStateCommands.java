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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class ReplaceSetOutputAndSaveStateCommands extends Recipe {

    private static final Pattern DEPRECATED_COMMAND = Pattern.compile(
            "(?<echo>\\becho\\s+)(?<quote>[\"'])(?<command>::(?:set-output|save-state))" +
                    "\\s+name=(?<name>[^:\\s]+)::(?<value>.*?)\\k<quote>");

    String displayName = "Replace deprecated `set-output` and `save-state` workflow commands";

    String description = "Rewrites the deprecated `::set-output` and `::save-state` workflow commands so they write to " +
                "the `GITHUB_OUTPUT` and `GITHUB_STATE` environment files. Those commands were deprecated in favor of " +
                "environment files and are no longer supported, so steps that still use them silently stop sharing " +
                "values. Steps that run on PowerShell are rewritten to use `$env:GITHUB_OUTPUT` and `$env:GITHUB_STATE`. " +
                "The replacement syntax requires GitHub Actions runner 2.297.0 or later, and writing multi-line values " +
                "requires a randomized delimiter that this recipe leaves to the reader. " +
                "[Read the deprecation announcement]" +
                "(https://github.blog/changelog/2022-10-11-github-actions-deprecating-save-state-and-set-output-commands/).";

    @Override
    public Set<String> getTags() {
        Set<String> tags = new HashSet<>();
        tags.add("github");
        tags.add("actions");
        return tags;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsWorkflow(), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                if (!"run".equals(entry.getKey().getValue()) || !(entry.getValue() instanceof Yaml.Scalar)) {
                    return super.visitMappingEntry(entry, ctx);
                }
                Yaml.Scalar scalar = (Yaml.Scalar) entry.getValue();
                String run = scalar.getValue();
                String rewritten = rewrite(run, runsOnPowerShell(getCursor()) ? "$env:" : "$");
                if (run.equals(rewritten)) {
                    return super.visitMappingEntry(entry, ctx);
                }
                return super.visitMappingEntry(entry.withValue(scalar.withValue(rewritten)), ctx);
            }
        });
    }

    private static String rewrite(String run, String variablePrefix) {
        Matcher matcher = DEPRECATED_COMMAND.matcher(run);
        if (!matcher.find()) {
            return run;
        }
        StringBuffer rewritten = new StringBuffer();
        do {
            String variable = "::save-state".equals(matcher.group("command")) ? "GITHUB_STATE" : "GITHUB_OUTPUT";
            String replacement = matcher.group("echo") + matcher.group("quote") +
                    matcher.group("name") + "=" + matcher.group("value") +
                    matcher.group("quote") + " >> " + variablePrefix + variable;
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        } while (matcher.find());
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private static boolean runsOnPowerShell(Cursor entryCursor) {
        Cursor stepCursor = entryCursor.getParent();
        if (stepCursor == null) {
            return false;
        }
        if (stepCursor.getValue() instanceof Yaml.Mapping) {
            String shell = scalarValue((Yaml.Mapping) stepCursor.getValue(), "shell");
            if (shell != null) {
                return isPowerShell(shell);
            }
        }
        for (Cursor cursor = stepCursor; cursor != null; cursor = cursor.getParent()) {
            if (cursor.getValue() instanceof Yaml.Mapping) {
                String shell = defaultsRunShell((Yaml.Mapping) cursor.getValue());
                if (shell != null) {
                    return isPowerShell(shell);
                }
            }
        }
        return false;
    }

    private static boolean isPowerShell(String shell) {
        return "pwsh".equals(shell) || "powershell".equals(shell);
    }

    private static @Nullable String defaultsRunShell(Yaml.Mapping mapping) {
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            if ("defaults".equals(entry.getKey().getValue()) && entry.getValue() instanceof Yaml.Mapping) {
                Yaml.Mapping defaults = (Yaml.Mapping) entry.getValue();
                for (Yaml.Mapping.Entry defaultsEntry : defaults.getEntries()) {
                    if ("run".equals(defaultsEntry.getKey().getValue()) && defaultsEntry.getValue() instanceof Yaml.Mapping) {
                        return scalarValue((Yaml.Mapping) defaultsEntry.getValue(), "shell");
                    }
                }
            }
        }
        return null;
    }

    private static @Nullable String scalarValue(Yaml.Mapping mapping, String key) {
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            if (key.equals(entry.getKey().getValue()) && entry.getValue() instanceof Yaml.Scalar) {
                return ((Yaml.Scalar) entry.getValue()).getValue();
            }
        }
        return null;
    }
}
