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

import java.util.regex.Pattern;

/**
 * Helpers for the ref portion of a GitHub Actions {@code uses:} reference
 * (e.g. the {@code v4} or commit SHA in {@code owner/repo@v4}).
 */
final class UsesRefs {

    private static final Pattern SHA_PATTERN = Pattern.compile("^[a-f0-9]{40}$");

    private UsesRefs() {
    }

    /**
     * @return the ref after the first {@code @} in a {@code uses:} value, or {@code null} when the
     * value has no {@code @}.
     */
    static @Nullable String refOf(String usesValue) {
        int at = usesValue.indexOf('@');
        return at < 0 ? null : usesValue.substring(at + 1);
    }

    /**
     * Decide whether a {@code uses:} value should be changed, given an {@code oldSha} sentinel that
     * mirrors the Docker {@code ChangeFrom} {@code oldDigest} option:
     * <ul>
     *     <li>{@code null} — change regardless of how the action is pinned (the default).</li>
     *     <li>empty string — change only refs that are <em>not</em> a 40-character commit SHA,
     *     preserving deliberate SHA pins.</li>
     *     <li>a concrete value — change only the entry whose ref equals that value.</li>
     * </ul>
     */
    static boolean matchesOldSha(@Nullable String oldSha, String usesValue) {
        String ref = refOf(usesValue);
        if (oldSha == null) {
            return true;
        }
        if (oldSha.isEmpty()) {
            return ref == null || !SHA_PATTERN.matcher(ref).matches();
        }
        return ref != null && ref.equals(oldSha);
    }
}
