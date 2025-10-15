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
package org.openrewrite.github.traits;

import org.jspecify.annotations.Nullable;
import org.openrewrite.yaml.tree.Yaml;

/**
 * A trait that provides safe access to scalar values from YAML LST elements.
 * This trait encapsulates common patterns for extracting string values from
 * YAML blocks, eliminating the need for repeated type checking and casting.
 *
 * <p>This trait is designed to be mixed into recipes or visitors that need to
 * work with YAML scalar values, providing a cleaner API than static utility methods.
 * It follows OpenRewrite best practices for trait-based recipe development.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * private static class MyVisitor extends YamlIsoVisitor&lt;ExecutionContext&gt;
 *         implements YamlScalarAccessor {
 *
 *     {@literal @}Override
 *     public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
 *         // Extract scalar value from mapping entry
 *         String value = getScalarValue(entry.getValue());
 *         if (value != null) {
 *             // Work with the scalar value
 *         }
 *         return super.visitMappingEntry(entry, ctx);
 *     }
 *
 *     {@literal @}Override
 *     public Yaml.Sequence visitSequence(Yaml.Sequence sequence, ExecutionContext ctx) {
 *         // Extract values from sequence entries
 *         for (Yaml.Sequence.Entry entry : sequence.getEntries()) {
 *             String value = getSequenceEntryValue(entry);
 *             if (value != null) {
 *                 // Process each value
 *             }
 *         }
 *         return super.visitSequence(sequence, ctx);
 *     }
 * }
 * </pre>
 *
 * <h2>Migration from YamlHelper</h2>
 * <p>This trait replaces the static {@code YamlHelper} utility class with a more
 * composable and testable approach. To migrate existing code:</p>
 * <pre>
 * // Before (using static utility):
 * String value = YamlHelper.getScalarValue(block);
 *
 * // After (using trait):
 * class MyVisitor extends YamlIsoVisitor&lt;ExecutionContext&gt;
 *         implements YamlScalarAccessor {
 *     void myMethod(Yaml.Block block) {
 *         String value = getScalarValue(block);
 *     }
 * }
 * </pre>
 *
 * @see org.openrewrite.yaml.tree.Yaml.Scalar
 * @see org.openrewrite.yaml.tree.Yaml.Block
 */
public interface YamlScalarAccessor {

    /**
     * Safely extracts the string value from a YAML block if it's a scalar.
     *
     * <p>This method performs type checking and casting in a single operation,
     * returning null if the block is not a Yaml.Scalar. This eliminates the need
     * for explicit instanceof checks and casts throughout recipe code.</p>
     *
     * <p>Example:
     * <pre>
     * Yaml.Block block = entry.getValue();
     * String value = getScalarValue(block);
     * if (value != null) {
     *     // block was a Yaml.Scalar with a non-null value
     * }
     * </pre>
     * </p>
     *
     * @param block The YAML block to extract value from, may be null
     * @return The string value if block is a Yaml.Scalar, null otherwise
     */
    default @Nullable String getScalarValue(Yaml.Block block) {
        return block instanceof Yaml.Scalar ? ((Yaml.Scalar) block).getValue() : null;
    }

    /**
     * Safely extracts the string value from a YAML scalar.
     *
     * <p>This is a convenience method for when you already know the element is
     * a scalar but want null-safe value extraction. Useful when working with
     * APIs that return {@code Yaml.Scalar} directly.</p>
     *
     * <p>Example:
     * <pre>
     * Yaml.Scalar scalar = (Yaml.Scalar) entry.getKey();
     * String keyValue = getScalarValue(scalar);
     * </pre>
     * </p>
     *
     * @param scalar The YAML scalar to extract value from, may be null
     * @return The string value if scalar is non-null, null otherwise
     */
    default @Nullable String getScalarValue(Yaml.Scalar scalar) {
        return scalar != null ? scalar.getValue() : null;
    }

    /**
     * Extracts scalar value from a mapping entry's value.
     *
     * <p>Common pattern when working with key-value pairs in YAML mappings.
     * Equivalent to calling {@code getScalarValue(entry.getValue())} but more
     * semantic and concise.</p>
     *
     * <p>Example:
     * <pre>
     * for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
     *     String value = getEntryScalarValue(entry);
     *     if (value != null) {
     *         // Process entry's value
     *     }
     * }
     * </pre>
     * </p>
     *
     * @param entry The mapping entry to extract value from, may be null
     * @return The string value if entry's value is a Yaml.Scalar, null otherwise
     */
    default @Nullable String getEntryScalarValue(Yaml.Mapping.Entry entry) {
        return entry != null ? getScalarValue(entry.getValue()) : null;
    }

    /**
     * Extracts scalar value from a sequence entry's block.
     *
     * <p>Common pattern when iterating over YAML sequences (arrays/lists).
     * Equivalent to calling {@code getScalarValue(entry.getBlock())} but more
     * semantic for sequence processing.</p>
     *
     * <p>Example:
     * <pre>
     * Yaml.Sequence sequence = (Yaml.Sequence) onValue;
     * for (Yaml.Sequence.Entry seqEntry : sequence.getEntries()) {
     *     String trigger = getSequenceEntryValue(seqEntry);
     *     if ("pull_request_target".equals(trigger)) {
     *         // Found dangerous trigger
     *     }
     * }
     * </pre>
     * </p>
     *
     * @param entry The sequence entry to extract value from, may be null
     * @return The string value if entry's block is a Yaml.Scalar, null otherwise
     */
    default @Nullable String getSequenceEntryValue(Yaml.Sequence.Entry entry) {
        return entry != null ? getScalarValue(entry.getBlock()) : null;
    }

    /**
     * Finds a scalar value for a given key in a YAML mapping.
     *
     * <p>This method searches through all entries in the mapping to find one
     * with the specified key, then extracts its scalar value if present. Returns
     * null if the key is not found or if the value is not a scalar.</p>
     *
     * <p>Example:
     * <pre>
     * Yaml.Mapping jobMapping = (Yaml.Mapping) jobEntry.getValue();
     * String runsOn = findScalarValueByKey(jobMapping, "runs-on");
     * if ("self-hosted".equals(runsOn)) {
     *     // Detected self-hosted runner
     * }
     * </pre>
     * </p>
     *
     * @param mapping The YAML mapping to search in
     * @param key The key to look for
     * @return The scalar value if found, null otherwise
     */
    default @Nullable String findScalarValueByKey(Yaml.Mapping mapping, String key) {
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            if (key.equals(entry.getKey().getValue())) {
                return getScalarValue(entry.getValue());
            }
        }
        return null;
    }

    /**
     * Finds a nested scalar value by traversing through a parent key to a child key.
     *
     * <p>This method handles the common pattern of accessing nested YAML values like
     * "with.path" by first finding the parent mapping, then searching for the child key.
     * Returns null if either the parent key is not found, the parent value is not a
     * mapping, the child key is not found, or the child value is not a scalar.</p>
     *
     * <p>Example usage for GitHub Actions workflow:
     * <pre>
     * # YAML:
     * - uses: actions/cache@v3
     *   with:
     *     path: ~/.cache
     *     key: cache-key
     *
     * # Code:
     * Yaml.Mapping stepMapping = (Yaml.Mapping) stepEntry.getBlock();
     * String cachePath = findNestedScalarValue(stepMapping, "with", "path");
     * // Returns: "~/.cache"
     * </pre>
     * </p>
     *
     * @param mapping The YAML mapping to search in
     * @param parentKey The parent key (e.g., "with")
     * @param childKey The child key (e.g., "path")
     * @return The nested scalar value if found, null otherwise
     */
    default @Nullable String findNestedScalarValue(Yaml.Mapping mapping, String parentKey, String childKey) {
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            if (parentKey.equals(entry.getKey().getValue()) && entry.getValue() instanceof Yaml.Mapping) {
                Yaml.Mapping parentMapping = (Yaml.Mapping) entry.getValue();
                return findScalarValueByKey(parentMapping, childKey);
            }
        }
        return null;
    }
}
