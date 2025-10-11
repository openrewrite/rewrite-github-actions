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

import org.jspecify.annotations.Nullable;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Optional;

/**
 * Utility class containing common patterns for working with YAML LST in OpenRewrite recipes.
 * These methods help eliminate code duplication and provide idiomatic ways to work with YAML structures.
 */
public final class YamlHelper {

    private YamlHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Safely extracts the string value from a YAML block if it's a scalar.
     *
     * @param block The YAML block to extract value from
     * @return The string value if block is a Yaml.Scalar, null otherwise
     */
    public static @Nullable String getScalarValue(Yaml.Block block) {
        return block instanceof Yaml.Scalar ? ((Yaml.Scalar) block).getValue() : null;
    }

    /**
     * Gets the first value from a YAML sequence if it exists and is a scalar.
     *
     * @param sequence The YAML sequence to extract from
     * @return Optional containing the first scalar value, or empty if sequence is empty or first element is not a scalar
     */
    public static Optional<String> getFirstSequenceValue(Yaml.Sequence sequence) {
        return sequence.getEntries().isEmpty() ? Optional.empty() :
            Optional.ofNullable(getScalarValue(sequence.getEntries().get(0).getBlock()));
    }

    /**
     * Finds a mapping entry with the given key in a YAML mapping.
     *
     * @param mapping The YAML mapping to search in
     * @param key The key to look for
     * @return The Yaml.Mapping if found, null otherwise
     */
    public static Yaml.@Nullable Mapping findMappingWithKey(Yaml.Mapping mapping, String key) {
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            if (key.equals(entry.getKey().getValue()) && entry.getValue() instanceof Yaml.Mapping) {
                return (Yaml.Mapping) entry.getValue();
            }
        }
        return null;
    }

    /**
     * Finds a scalar value for a given key in a YAML mapping.
     *
     * @param mapping The YAML mapping to search in
     * @param key The key to look for
     * @return The scalar value if found, null otherwise
     */
    public static @Nullable String findScalarValue(Yaml.Mapping mapping, String key) {
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            if (key.equals(entry.getKey().getValue())) {
                return getScalarValue(entry.getValue());
            }
        }
        return null;
    }

    /**
     * Finds a nested scalar value by traversing through a parent key to a child key.
     * For example, findNestedScalarValue(mapping, "with", "path") would find the value at with.path
     *
     * @param mapping The YAML mapping to search in
     * @param parentKey The parent key (e.g., "with")
     * @param childKey The child key (e.g., "path")
     * @return The nested scalar value if found, null otherwise
     */
    public static @Nullable String findNestedScalarValue(Yaml.Mapping mapping, String parentKey, String childKey) {
        Yaml.Mapping parentMapping = findMappingWithKey(mapping, parentKey);
        if (parentMapping == null) {
            return null;
        }
        return findScalarValue(parentMapping, childKey);
    }

    /**
     * Checks if a mapping contains an entry with the given key.
     *
     * @param mapping The YAML mapping to check
     * @param key The key to look for
     * @return true if the mapping contains an entry with the key, false otherwise
     */
    public static boolean hasKey(Yaml.Mapping mapping, String key) {
        return mapping.getEntries().stream()
                .anyMatch(entry -> key.equals(entry.getKey().getValue()));
    }
}
