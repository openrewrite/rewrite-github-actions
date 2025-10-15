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
package org.openrewrite.github.util;

import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.trait.Trait;
import org.openrewrite.trait.VisitFunction2;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

/**
 * Represents a GitHub Actions workflow step that uses an external action (has a `uses` key).
 * <p>
 * This trait provides <strong>read-only</strong> semantic methods for querying action references,
 * versions, and metadata without manual cursor navigation or instanceof checks.
 * <p>
 * <strong>Design Principle:</strong> This trait is designed for querying and matching only.
 * For tree modifications, use the trait to identify targets, then build updated trees
 * directly in your visitor and return them.
 * <p>
 * Example workflow step:
 * <pre>
 * - uses: actions/checkout@v3
 * - uses: actions/setup-java@a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0
 * </pre>
 * <p>
 * Example usage:
 * <pre>
 * new ActionStep.Matcher()
 *     .withRequiredAction("actions/checkout")
 *     .asVisitor((actionStep, ctx) -> {
 *         if (actionStep.isVersionPinned()) {
 *             // Read-only operations
 *             String owner = actionStep.getActionOwner();
 *             String version = actionStep.getActionVersion();
 *         }
 *         return actionStep.getTree();
 *     })
 * </pre>
 */
@AllArgsConstructor
public class ActionStep implements Trait<Yaml.Mapping.Entry> {
    Cursor cursor;

    @Override
    public Cursor getCursor() {
        return cursor;
    }

    /**
     * Get the full action reference (e.g., "actions/checkout@v3").
     *
     * @return The full action reference, or null if not available
     */
    public @Nullable String getActionRef() {
        Yaml.Mapping.Entry entry = getTree();
        if (entry.getValue() instanceof Yaml.Scalar) {
            return ((Yaml.Scalar) entry.getValue()).getValue();
        }
        return null;
    }

    /**
     * Get the action owner (e.g., "actions" from "actions/checkout@v3").
     *
     * @return The action owner, or null if not available or for local/docker actions
     */
    public @Nullable String getActionOwner() {
        String ref = getActionRef();
        if (ref == null || !ref.contains("/")) {
            return null;
        }
        // Skip local and docker actions
        if (ref.startsWith("./") || ref.startsWith("docker://")) {
            return null;
        }
        String nameWithOwner = ref.contains("@") ? ref.substring(0, ref.indexOf("@")) : ref;
        return nameWithOwner.substring(0, nameWithOwner.indexOf("/"));
    }

    /**
     * Get the action name without version (e.g., "actions/checkout" from "actions/checkout@v3").
     *
     * @return The action name, or null if not available
     */
    public @Nullable String getActionName() {
        String ref = getActionRef();
        if (ref == null) {
            return null;
        }
        return ref.contains("@") ? ref.substring(0, ref.indexOf("@")) : ref;
    }

    /**
     * Get the action version (e.g., "v3" from "actions/checkout@v3").
     *
     * @return The action version, or null if not specified
     */
    public @Nullable String getActionVersion() {
        String ref = getActionRef();
        if (ref == null || !ref.contains("@")) {
            return null;
        }
        return ref.substring(ref.indexOf("@") + 1);
    }

    /**
     * Check if the action is pinned to a full 40-character SHA.
     *
     * @return true if pinned to a SHA, false otherwise
     */
    public boolean isVersionPinned() {
        String version = getActionVersion();
        return version != null && version.matches("[a-f0-9]{40}");
    }

    /**
     * Check if this action matches a given action pattern.
     * <p>
     * Supports patterns like:
     * <ul>
     *   <li>"actions/checkout" - exact match</li>
     *   <li>"actions/checkout@*" - match any version</li>
     * </ul>
     *
     * @param actionPattern The pattern to match
     * @return true if matches, false otherwise
     */
    public boolean matchesAction(String actionPattern) {
        String name = getActionName();
        if (name == null) {
            return false;
        }
        if (actionPattern.endsWith("@*")) {
            return name.equals(actionPattern.substring(0, actionPattern.length() - 2));
        }
        return name.equals(actionPattern);
    }

    /**
     * Check if a cursor points to an action step (a mapping entry with key "uses").
     *
     * @param cursor The cursor to check
     * @return true if it's an action step, false otherwise
     */
    public static boolean isActionStep(Cursor cursor) {
        if (!(cursor.getValue() instanceof Yaml.Mapping.Entry)) {
            return false;
        }
        Yaml.Mapping.Entry entry = cursor.getValue();
        return entry.getKey() instanceof Yaml.Scalar &&
                "uses".equals(((Yaml.Scalar) entry.getKey()).getValue());
    }

    /**
     * Check if a cursor is within a steps array of a job.
     *
     * @param cursor The cursor to check
     * @return true if within a steps array, false otherwise
     */
    public static boolean withinStepsArray(Cursor cursor) {
        Cursor parent = cursor.getParent();
        while (parent != null) {
            if (parent.getValue() instanceof Yaml.Mapping.Entry) {
                Yaml.Mapping.Entry entry = parent.getValue();
                if (entry.getKey() instanceof Yaml.Scalar &&
                        "steps".equals(((Yaml.Scalar) entry.getKey()).getValue())) {
                    return true;
                }
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Matcher for finding ActionStep traits in YAML documents.
     */
    public static class Matcher extends YamlTraitMatcher<ActionStep> {
        @Nullable
        protected String requiredAction;

        /**
         * Filter to match only a specific action.
         *
         * @param action The action name to match (e.g., "actions/checkout")
         * @return This matcher for chaining
         */
        public Matcher withRequiredAction(String action) {
            this.requiredAction = action;
            return this;
        }

        @Override
        protected @Nullable ActionStep test(Cursor cursor) {
            // Must be a mapping entry with key "uses"
            if (!isActionStep(cursor)) {
                return null;
            }

            // Must be within a steps array
            if (!withinStepsArray(cursor)) {
                return null;
            }

            // Create trait instance
            ActionStep trait = new ActionStep(cursor);

            // Apply filter if configured
            if (requiredAction != null && !trait.matchesAction(requiredAction)) {
                return null;
            }

            return trait;
        }

        /**
         * Override asVisitor for better performance - only visit mapping entries.
         */
        @Override
        public <P> TreeVisitor<? extends Tree, P> asVisitor(VisitFunction2<ActionStep, P> visitor) {
            return new YamlIsoVisitor<P>() {
                @Override
                public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, P p) {
                    ActionStep actionStep = test(getCursor());
                    if (actionStep != null) {
                        return (Yaml.Mapping.Entry) visitor.visit(actionStep, p);
                    }
                    return super.visitMappingEntry(entry, p);
                }
            };
        }
    }
}
