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

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;

/**
 * Resolves the newest known version of an official GitHub Action from the static
 * {@code known-action-shas.properties} mapping, without contacting the network. Only actions in
 * the {@code actions} and {@code github} organizations are indexed; everything else is ignored.
 */
final class OfficialActionVersions {

    static final Set<String> OFFICIAL_ORGS = unmodifiableSet(new HashSet<>(asList("actions", "github")));

    private static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern MAJOR = Pattern.compile("v?\\d+");
    private static final Pattern MINOR = Pattern.compile("v?\\d+\\.\\d+");
    private static final Pattern PATCH = Pattern.compile("v?\\d+\\.\\d+\\.\\d+");

    private final Map<String, Versions> byAction;

    private OfficialActionVersions(Map<String, Versions> byAction) {
        this.byAction = byAction;
    }

    static boolean isOfficial(String actionPath) {
        int slash = actionPath.indexOf('/');
        return OFFICIAL_ORGS.contains(slash < 0 ? actionPath : actionPath.substring(0, slash));
    }

    static OfficialActionVersions fromProperties(Map<String, String> knownShas) {
        Map<String, Versions> byAction = new HashMap<>();
        for (Map.Entry<String, String> e : knownShas.entrySet()) {
            String key = e.getKey();
            int at = key.indexOf('@');
            if (at < 0) {
                continue;
            }
            String actionPath = key.substring(0, at);
            if (!isOfficial(actionPath)) {
                continue;
            }
            String ref = key.substring(at + 1);
            Versions versions = byAction.computeIfAbsent(actionPath, k -> new Versions());
            if (PATCH.matcher(ref).matches()) {
                versions.patch = max(versions.patch, ref);
            } else if (MINOR.matcher(ref).matches()) {
                versions.minor = max(versions.minor, ref);
            } else if (MAJOR.matcher(ref).matches()) {
                if (ref.equals(max(versions.major, ref))) {
                    versions.major = ref;
                    versions.majorSha = e.getValue();
                }
            }
        }
        return new OfficialActionVersions(byAction);
    }

    /**
     * @return the newest version to upgrade {@code currentRef} to, matching its precision (major,
     * minor, patch, or commit SHA) and preserving any {@code v} prefix, or {@code null} when the
     * action is unknown, the ref is not an upgradeable version, or it is already up to date.
     */
    @Nullable String upgrade(String actionPath, String currentRef) {
        Versions versions = byAction.get(actionPath);
        if (versions == null) {
            return null;
        }
        if (SHA.matcher(currentRef).matches()) {
            return versions.majorSha != null && !versions.majorSha.equals(currentRef) ? versions.majorSha : null;
        }

        String target;
        if (PATCH.matcher(currentRef).matches()) {
            target = versions.patch;
        } else if (MINOR.matcher(currentRef).matches()) {
            target = versions.minor;
        } else if (MAJOR.matcher(currentRef).matches()) {
            target = versions.major;
        } else {
            return null;
        }
        if (target == null || compare(target, currentRef) <= 0) {
            return null;
        }
        return currentRef.startsWith("v") ? target : target.substring(1);
    }

    private static String max(@Nullable String current, String candidate) {
        return current == null || compare(candidate, current) > 0 ? candidate : current;
    }

    private static int compare(String a, String b) {
        int[] left = parse(a);
        int[] right = parse(b);
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            int c = Integer.compare(left[i], right[i]);
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static int[] parse(String version) {
        String[] parts = (version.startsWith("v") ? version.substring(1) : version).split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return numbers;
    }

    private static final class Versions {
        @Nullable String major;
        @Nullable String majorSha;
        @Nullable String minor;
        @Nullable String patch;
    }
}
