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
package org.openrewrite.github.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/// Guards `known-action-shas.properties`, which is regenerated and committed by the
/// `update-known-shas` workflow without review.
class KnownActionShasTest {

    private static final List<String> ENTRIES = readEntries();
    private static final Map<String, String> SHAS = toMap(ENTRIES);

    @Test
    void entriesAreWellFormed() {
        assertThat(ENTRIES.size()).isGreaterThan(10_000);
        assertThat(ENTRIES)
          .filteredOn(line -> !line.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(/[^@=\\s]+)?@[^=\\s]+=[0-9a-f]{40}"))
          .isEmpty();
    }

    @Test
    void keysAreUnique() {
        Set<String> seen = new HashSet<>();
        assertThat(ENTRIES.stream().map(line -> line.substring(0, line.indexOf('='))).filter(key -> !seen.add(key)).toList())
          .isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
      "actions/checkout@v4.2.2, 11bd71901bbe5b1630ceea73d27597364c9af683",
      "docker/build-push-action@v6.0.0, c382f710d39a5bb4e430307530a720f50c2d3318",
      "github/codeql-action/init@v3.28.0, 48ab28a6f5dbc2a99bf1e0131198dd8f1df78169",
      "pypa/gh-action-pypi-publish@v1.12.4, 76f52bc884231f62b9a034ebfe128415bbaabdfc",
      "bazel-contrib/setup-bazel@0.15.0, 4fd964a13a440a8aeb0be47350db2fc640f19ca8",
      "ramsey/composer-install@3.0.0, 57532f8be5bda426838819c5ee9afb8af389d51a",
    })
    void immutableTagsResolveToTheirCommit(String ref, String sha) {
        assertThat(SHAS.get(ref)).as(ref).isEqualTo(sha);
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "dtolnay/rust-toolchain@stable",
      "pypa/gh-action-pypi-publish@release/v1",
      "ramsey/composer-install@v3",
    })
    void branchRefsArePresent(String ref) {
        assertThat(SHAS.get(ref)).as(ref).isNotNull();
    }

    @Test
    void majorTagMatchesAReleaseOfThatMajor() {
        String major = SHAS.get("actions/checkout@v4");
        assertThat(major).isNotNull();
        assertThat(SHAS.entrySet().stream()
          .filter(e -> e.getKey().matches("actions/checkout@v4\\.\\d+\\.\\d+"))
          .map(Map.Entry::getValue)
          .toList())
          .contains(major);
    }

    private static List<String> readEntries() {
        try (InputStream is = Objects.requireNonNull(KnownActionShasTest.class
                .getResourceAsStream("/META-INF/rewrite/known-action-shas.properties"));
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8))) {
            return reader.lines()
              .filter(line -> !line.isEmpty() && !line.startsWith("#"))
              .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, String> toMap(List<String> entries) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : entries) {
            int eq = line.indexOf('=');
            map.put(line.substring(0, eq), line.substring(eq + 1));
        }
        return map;
    }
}
