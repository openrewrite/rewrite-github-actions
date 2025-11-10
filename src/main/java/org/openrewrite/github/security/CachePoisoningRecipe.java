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
package org.openrewrite.github.security;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.github.IsGitHubActionsWorkflow;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class CachePoisoningRecipe extends Recipe {

    // Actions that have caching capabilities and could be vulnerable
    private static final Set<String> CACHE_AWARE_ACTIONS = new HashSet<>(Arrays.asList(
            "actions/cache",
            "actions/setup-java",
            "actions/setup-go",
            "actions/setup-node",
            "actions/setup-python",
            "actions/setup-dotnet",
            "astral-sh/setup-uv",
            "Swatinem/rust-cache",
            "ruby/setup-ruby",
            "PyO3/maturin-action",
            "mlugg/setup-zig",
            "oven-sh/setup-bun",
            "DeterminateSystems/magic-nix-cache-action",
            "graalvm/setup-graalvm",
            "gradle/actions/setup-gradle",
            "docker/setup-buildx-action",
            "actions-rust-lang/setup-rust-toolchain",
            "Mozilla-Actions/sccache-action",
            "nix-community/cache-nix-action",
            "jdx/mise-action"
    ));

    // Actions that typically publish artifacts
    private static final Set<String> PUBLISHER_ACTIONS = new HashSet<>(Arrays.asList(
            "pypa/gh-action-pypi-publish",
            "rubygems/release-gem",
            "jreleaser/release-action",
            "goreleaser/goreleaser-action",
            "softprops/action-gh-release",
            "release-drafter/release-drafter",
            "googleapis/release-please-action",
            "docker/build-push-action",
            "redhat-actions/push-to-registry"
    ));

    private static final Pattern RELEASE_BRANCH_PATTERN = Pattern.compile(".*release.*", Pattern.CASE_INSENSITIVE);

    @Override
    public String getDisplayName() {
        return "Find cache poisoning vulnerabilities";
    }

    @Override
    public String getDescription() {
        return "Detects potential cache poisoning vulnerabilities in workflows that use caching and publish artifacts. " +
                "When workflows use caches during artifact publishing, attackers may be able to poison the cache " +
                "with malicious content that gets included in published artifacts. " +
                "Based on [zizmor's cache-poisoning audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/cache_poisoning.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsGitHubActionsWorkflow(), new YamlIsoVisitor<ExecutionContext>() {
            private boolean isPublishingWorkflow = false;
            private boolean hasPublisherAction = false;

            @Override
            public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                // Reset state for each document
                isPublishingWorkflow = false;
                hasPublisherAction = false;

                // First pass: determine if this is a publishing workflow
                analyzeWorkflow(document);

                // Second pass: if it's a publishing workflow, look for cache usage
                if (isPublishingWorkflow || hasPublisherAction) {
                    return super.visitDocument(document, ctx);
                }

                return document;
            }

            private void analyzeWorkflow(Yaml.Document document) {
                if (document.getBlock() instanceof Yaml.Mapping) {
                    Yaml.Mapping workflowMapping = (Yaml.Mapping) document.getBlock();

                    for (Yaml.Mapping.Entry entry : workflowMapping.getEntries()) {
                        if (entry.getKey() instanceof Yaml.Scalar) {
                            String key = ((Yaml.Scalar) entry.getKey()).getValue();

                            if ("on".equals(key)) {
                                isPublishingWorkflow = isPublishingTrigger(entry.getValue());
                            } else if ("jobs".equals(key)) {
                                hasPublisherAction = hasPublisherActions(entry.getValue());
                            }
                        }
                    }
                }
            }

            private boolean isPublishingTrigger(Yaml.Block onValue) {
                String scalarTrigger = YamlHelper.getScalarValue(onValue);
                if (scalarTrigger != null) {
                    return "release".equals(scalarTrigger);
                }
                if (onValue instanceof Yaml.Sequence) {
                    Yaml.Sequence sequence = (Yaml.Sequence) onValue;
                    for (Yaml.Sequence.Entry seqEntry : sequence.getEntries()) {
                        String trigger = YamlHelper.getScalarValue(seqEntry.getBlock());
                        if ("release".equals(trigger)) {
                            return true;
                        }
                    }
                } else if (onValue instanceof Yaml.Mapping) {
                    Yaml.Mapping mapping = (Yaml.Mapping) onValue;
                    for (Yaml.Mapping.Entry triggerEntry : mapping.getEntries()) {
                        String trigger = triggerEntry.getKey().getValue();
                        if ("release".equals(trigger)) {
                            return true;
                        }
                        if ("push".equals(trigger)) {
                            // Check for release branches or tags
                            if (isReleasePush(triggerEntry.getValue())) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            private boolean isReleasePush(Yaml.Block pushConfig) {
                if (pushConfig instanceof Yaml.Mapping) {
                    Yaml.Mapping mapping = (Yaml.Mapping) pushConfig;
                    for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
                        if (entry.getKey() instanceof Yaml.Scalar) {
                            String key = ((Yaml.Scalar) entry.getKey()).getValue();
                            if ("tags".equals(key)) {
                                return true; // Pushing tags suggests release
                            }
                            if ("branches".equals(key)) {
                                // Check if any branch name suggests release
                                return hasReleaseBranches(entry.getValue());
                            }
                        }
                    }
                }
                return false;
            }

            private boolean hasReleaseBranches(Yaml.Block branchesValue) {
                if (branchesValue instanceof Yaml.Sequence) {
                    Yaml.Sequence sequence = (Yaml.Sequence) branchesValue;
                    for (Yaml.Sequence.Entry entry : sequence.getEntries()) {
                        String branch = YamlHelper.getScalarValue(entry.getBlock());
                        if (branch != null && RELEASE_BRANCH_PATTERN.matcher(branch).matches()) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private boolean hasPublisherActions(Yaml.Block jobsValue) {
                if (jobsValue instanceof Yaml.Mapping) {
                    Yaml.Mapping jobsMapping = (Yaml.Mapping) jobsValue;
                    for (Yaml.Mapping.Entry jobEntry : jobsMapping.getEntries()) {
                        if (jobEntry.getValue() instanceof Yaml.Mapping) {
                            Yaml.Mapping jobMapping = (Yaml.Mapping) jobEntry.getValue();
                            if (jobHasPublisherAction(jobMapping)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            private boolean jobHasPublisherAction(Yaml.Mapping jobMapping) {
                for (Yaml.Mapping.Entry entry : jobMapping.getEntries()) {
                    if (entry.getKey() instanceof Yaml.Scalar && "steps".equals(((Yaml.Scalar) entry.getKey()).getValue())) {
                        if (entry.getValue() instanceof Yaml.Sequence) {
                            Yaml.Sequence stepsSequence = (Yaml.Sequence) entry.getValue();
                            for (Yaml.Sequence.Entry stepEntry : stepsSequence.getEntries()) {
                                if (stepEntry.getBlock() instanceof Yaml.Mapping) {
                                    Yaml.Mapping stepMapping = (Yaml.Mapping) stepEntry.getBlock();
                                    if (stepUsesPublisherAction(stepMapping)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
                return false;
            }

            private boolean stepUsesPublisherAction(Yaml.Mapping stepMapping) {
                for (Yaml.Mapping.Entry entry : stepMapping.getEntries()) {
                    if ("uses".equals(entry.getKey().getValue())) {
                        String uses = YamlHelper.getScalarValue(entry.getValue());
                        if (uses != null) {
                            String actionName = extractActionName(uses);
                            return PUBLISHER_ACTIONS.contains(actionName);
                        }
                    }
                }
                return false;
            }

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

                // Look for cache-aware actions in steps
                if (isCacheAwareActionStep(mappingEntry)) {
                    String actionName = getActionName(mappingEntry);
                    return SearchResult.found(mappingEntry,
                            String.format("Action '%s' uses caching in a workflow that publishes artifacts. " +
                                    "This could lead to cache poisoning where malicious content gets cached and " +
                                    "included in published artifacts. Consider disabling caching for this step " +
                                    "or using read-only cache mode.", actionName));
                }

                return mappingEntry;
            }

            private boolean isCacheAwareActionStep(Yaml.Mapping.Entry entry) {
                if (!"uses".equals(entry.getKey().getValue())) {
                    return false;
                }

                String uses = YamlHelper.getScalarValue(entry.getValue());
                if (uses == null) {
                    return false;
                }

                String actionName = extractActionName(uses);
                return CACHE_AWARE_ACTIONS.contains(actionName);
            }

            private String getActionName(Yaml.Mapping.Entry entry) {
                String uses = YamlHelper.getScalarValue(entry.getValue());
                return uses != null ? extractActionName(uses) : "unknown";
            }

            private String extractActionName(String uses) {
                // Extract action name from "owner/repo@version" format
                if (uses.contains("@")) {
                    uses = uses.substring(0, uses.indexOf("@"));
                }
                return uses;
            }
        });
    }

}
