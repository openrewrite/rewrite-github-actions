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

import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableMap;

// Ecosystems absent from this map are deliberately left alone rather than mapped to a broader
// pattern such as the whole directory, which would grant ownership beyond what Dependabot covered.
final class DependabotEcosystemManifests {

    static final String GITHUB_ACTIONS = "github-actions";

    private static final Map<String, List<String>> MANIFESTS;

    static {
        Map<String, List<String>> manifests = new LinkedHashMap<>();
        manifests.put("bundler", asList("Gemfile", "Gemfile.lock"));
        manifests.put("cargo", asList("Cargo.toml", "Cargo.lock"));
        manifests.put("composer", asList("composer.json", "composer.lock"));
        manifests.put("devcontainers", asList(".devcontainer/devcontainer.json", ".devcontainer.json"));
        manifests.put("docker", asList("Dockerfile"));
        manifests.put("docker-compose", asList("docker-compose.yml", "docker-compose.yaml"));
        manifests.put("elm", asList("elm.json"));
        manifests.put("gitsubmodule", asList(".gitmodules"));
        manifests.put("gomod", asList("go.mod", "go.sum"));
        manifests.put("gradle", asList("build.gradle", "build.gradle.kts", "gradle/libs.versions.toml"));
        manifests.put("helm", asList("Chart.yaml", "Chart.lock"));
        manifests.put("maven", asList("pom.xml"));
        manifests.put("mix", asList("mix.exs", "mix.lock"));
        manifests.put("npm", asList("package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml"));
        manifests.put("nuget", asList("*.csproj", "packages.config"));
        manifests.put("pip", asList("requirements.txt", "pyproject.toml", "Pipfile", "Pipfile.lock"));
        manifests.put("pub", asList("pubspec.yaml", "pubspec.lock"));
        manifests.put("swift", asList("Package.swift", "Package.resolved"));
        manifests.put("terraform", asList("*.tf"));
        manifests.put("uv", asList("pyproject.toml", "uv.lock"));
        MANIFESTS = unmodifiableMap(manifests);
    }

    private DependabotEcosystemManifests() {
    }

    static boolean isKnown(@Nullable String ecosystem) {
        return GITHUB_ACTIONS.equals(ecosystem) || MANIFESTS.containsKey(ecosystem);
    }

    static List<String> patternsFor(@Nullable String ecosystem, String directory) {
        if (GITHUB_ACTIONS.equals(ecosystem)) {
            return asList("/.github/workflows/");
        }
        List<String> manifests = MANIFESTS.get(ecosystem);
        if (manifests == null) {
            return Collections.emptyList();
        }
        String prefix = normalizeDirectory(directory);
        List<String> patterns = new ArrayList<>(manifests.size());
        for (String manifest : manifests) {
            patterns.add(prefix + manifest);
        }
        return patterns;
    }

    // Dependabot directories are repository-root relative and not searched recursively, so they
    // map onto CODEOWNERS patterns anchored with a leading slash
    private static String normalizeDirectory(String directory) {
        String trimmed = directory.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "/" : "/" + trimmed + "/";
    }
}
