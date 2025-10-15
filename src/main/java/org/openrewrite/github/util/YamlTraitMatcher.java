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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.SourceFile;
import org.openrewrite.trait.SimpleTraitMatcher;
import org.openrewrite.trait.Trait;
import org.openrewrite.yaml.tree.Yaml;

/**
 * Base matcher for YAML traits in GitHub Actions workflows.
 * Provides common utility methods for cursor navigation and value extraction.
 *
 * @param <U> The trait type being matched
 */
public abstract class YamlTraitMatcher<U extends Trait<?>> extends SimpleTraitMatcher<U> {

    /**
     * Check if the cursor is within a YAML mapping.
     *
     * @param cursor The cursor to check
     * @return true if within a mapping, false otherwise
     */
    protected boolean withinMapping(Cursor cursor) {
        return cursor.firstEnclosing(Yaml.Mapping.class) != null;
    }

    /**
     * Validate that the cursor is in a valid YAML context.
     *
     * @param cursor The cursor to validate
     * @return true if in a valid YAML document, false otherwise
     */
    protected boolean isValidYamlContext(Cursor cursor) {
        SourceFile sourceFile = cursor.firstEnclosing(SourceFile.class);
        return sourceFile instanceof Yaml.Documents;
    }

    /**
     * Safely extract a scalar value from a YAML block.
     *
     * @param block The YAML block to extract from
     * @return The scalar value, or null if not a scalar
     */
    protected @Nullable String getScalarValue(Yaml.Block block) {
        return block instanceof Yaml.Scalar ? ((Yaml.Scalar) block).getValue() : null;
    }

    /**
     * Get the scalar key from a mapping entry.
     *
     * @param entry The mapping entry
     * @return The key as a string, or null if not a scalar key
     */
    protected @Nullable String getScalarKey(Yaml.Mapping.Entry entry) {
        return entry.getKey() instanceof Yaml.Scalar ? ((Yaml.Scalar) entry.getKey()).getValue() : null;
    }

    /**
     * Find a scalar value by key in a mapping.
     *
     * @param mapping The mapping to search
     * @param key     The key to find
     * @return The scalar value, or null if not found or not a scalar
     */
    protected @Nullable String findScalarValueByKey(Yaml.Mapping mapping, String key) {
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            if (key.equals(getScalarKey(entry))) {
                return getScalarValue(entry.getValue());
            }
        }
        return null;
    }
}
