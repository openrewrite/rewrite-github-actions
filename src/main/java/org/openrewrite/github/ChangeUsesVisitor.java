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
import org.openrewrite.ExecutionContext;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.function.UnaryOperator;

/**
 * Rewrites the value of the {@code uses:} entries selected by a JsonPath, applying a per-entry
 * value transform. Entries are only rewritten when {@link UsesRefs#matchesOldSha} accepts the
 * current value, so SHA pins can be preserved via the {@code oldSha} sentinel. Shared by
 * {@link ChangeAction} and {@link ChangeActionVersion}; the two recipes differ only in the
 * {@code rename} function they supply.
 */
class ChangeUsesVisitor extends YamlIsoVisitor<ExecutionContext> {

    private final JsonPathMatcher matcher;

    @Nullable
    private final String oldSha;

    private final UnaryOperator<String> rename;

    ChangeUsesVisitor(String usesPath, @Nullable String oldSha, UnaryOperator<String> rename) {
        this.matcher = new JsonPathMatcher(usesPath);
        this.oldSha = oldSha;
        this.rename = rename;
    }

    @Override
    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
        Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
        if (!(matcher.matches(getCursor()) && e.getValue() instanceof Yaml.Scalar)) {
            return e;
        }

        Yaml.Scalar scalar = (Yaml.Scalar) e.getValue();
        String current = scalar.getValue();
        if (!UsesRefs.matchesOldSha(oldSha, current)) {
            return e;
        }

        String newValue = rename.apply(current);
        if (newValue.equals(current)) {
            return e;
        }

        return e.withValue(scalar.withValue(newValue));
    }
}
