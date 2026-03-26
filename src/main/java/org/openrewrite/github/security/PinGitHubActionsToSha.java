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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.github.IsGitHubActionsWorkflow;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class PinGitHubActionsToSha extends Recipe {

    private static final Pattern SHA_PATTERN = Pattern.compile("^[a-f0-9]{40}$");
    private static final Pattern USES_PATTERN = Pattern.compile("^([^/@]+/[^/@]+(?:/[^@]+)?)@(.+)$");

    /**
     * Official GitHub-maintained action organizations.
     * These are skipped by default unless {@code pinOfficialActions} is set.
     */
    private static final Set<String> OFFICIAL_ORGS = Set.of(
            "actions",
            "github"
    );

    /**
     * Static mapping of well-known third-party action references to their commit SHAs.
     * Checked first before falling back to the GitHub API.
     * Format: "owner/repo@ref" -> "sha"
     */
    private static final Map<String, String> KNOWN_SHAS = loadKnownShas();

    @Option(displayName = "Pin official actions",
            description = "When set to `true`, also pins actions from official GitHub organizations " +
                    "(e.g., `actions/*`, `github/*`). Defaults to `false`, meaning only third-party " +
                    "actions are pinned.",
            required = false)
    @Nullable
    Boolean pinOfficialActions;

    @Option(displayName = "GitHub API token",
            description = "A GitHub personal access token used to resolve tags/branches to commit SHAs " +
                    "via the GitHub API. Only needed for actions not found in the built-in static mapping. " +
                    "Without a token, unauthenticated requests are rate-limited to 60/hour.",
            required = false)
    @Nullable
    String githubApiToken;

    @Override
    public String getDisplayName() {
        return "Pin GitHub Actions to commit SHAs";
    }

    @Override
    public String getDescription() {
        return "Replaces mutable tag or branch references in GitHub Actions `uses:` declarations with " +
                "immutable commit SHAs. A static mapping of well-known actions is checked first; if " +
                "the action is not found, the GitHub API is used to resolve the reference at recipe " +
                "run time. By default only third-party actions are pinned; set `pinOfficialActions` " +
                "to include actions from the `actions` and `github` organizations.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("github", "actions", "security", "supply-chain");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        boolean pinOfficial = Boolean.TRUE.equals(pinOfficialActions);
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new PinActionsVisitor(pinOfficial, githubApiToken)
        );
    }

    /**
     * Loads the static SHA mapping from a bundled JSON resource file.
     * Falls back to a hardcoded set if the resource is not found.
     */
    private static Map<String, String> loadKnownShas() {
        Map<String, String> map = new LinkedHashMap<>();

        // Try loading from a bundled resource first
        try (InputStream is = PinGitHubActionsToSha.class
                .getResourceAsStream("/META-INF/rewrite/known-action-shas.json")) {
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, String> loaded = mapper.readValue(is,
                        new TypeReference<Map<String, String>>() {});
                map.putAll(loaded);
                return Collections.unmodifiableMap(map);
            }
        } catch (IOException ignored) {
            // Fall through to hardcoded defaults
        }

        // Hardcoded defaults for common third-party actions
        // These are resolved as of 2025-Q1 and should be refreshed periodically.

        // Codecov
        map.put("codecov/codecov-action@v4",
                "e28ff129e5465c2c0dcc6f003fc735cb6ae0c673");

        // Docker
        map.put("docker/setup-buildx-action@v3",
                "988b5a0280414f521407ac0e6c68267159f40517");
        map.put("docker/build-push-action@v5",
                "4a13e500e55cf31b7a5d59a38ab2040ab0f42f56");
        map.put("docker/login-action@v3",
                "343f7c4344506bcbf9b4de18042ae17996df046d");

        // Google Auth
        map.put("google-github-actions/auth@v2",
                "6fc4af4b145ae7821d527454aa9bd537d1f2dc5f");
        map.put("google-github-actions/setup-gcloud@v2",
                "6189d56e4096ee891640a1c3e2ed31e01bb59adf");

        // AWS
        map.put("aws-actions/configure-aws-credentials@v4",
                "e3dd6a429d7300a6a4c196c26e071d42e0343502");

        // Gradle
        map.put("gradle/gradle-build-action@v3",
                "ac2d340dc04d9e1f45502ab9e6b3adb3a7e3cc58");
        map.put("gradle/actions/setup-gradle@v3",
                "d9336dac04dea2507a617466bc058a3def92b18b");

        // JetBrains
        map.put("JetBrains/qodana-action@v2024.2",
                "d97381a3f2082a3f95c80aab2a1a92432aeb9f1e");

        // Hashicorp
        map.put("hashicorp/setup-terraform@v3",
                "b9cd54a3c349d3f85c46df8b5bae8a4e0ac0040f");

        // SonarSource
        map.put("SonarSource/sonarcloud-github-action@v3",
                "383f7e52eae3ab0510c3cb0e7d9d150bbaeec5cf");

        // Snyk
        map.put("snyk/actions/node@master",
                "1d672a455ab3339ef0a0021e1ec809475a5e7ef7");

        // Aqua Security
        map.put("aquasecurity/trivy-action@master",
                "18f2510ee396bbf400402947e029820369d39e1f");

        // Softprops (GitHub Release)
        map.put("softprops/action-gh-release@v2",
                "c95fe1489396fe8a9eb87c0abf8aa5b2ef267fda");
        map.put("softprops/action-gh-release@v1",
                "de2c0eb89ae2a093876385947365aca7b0e5f844");

        // Peter Evans (Create PR)
        map.put("peter-evans/create-pull-request@v6",
                "5e914681df9dc83aa4e4905692ca88beb2f9e681");
        map.put("peter-evans/create-pull-request@v5",
                "153407881ec5c347639a548ade7d8ad1d6740e38");

        // Dorny (Paths Filter)
        map.put("dorny/paths-filter@v3",
                "de90cc6fb38fc0963ad72b210f1f284cd68cea36");

        // Peaceiris (GitHub Pages)
        map.put("peaceiris/actions-gh-pages@v4",
                "4f9cc6602d3f66b9c108549d475ec49e8ef4d45e");
        map.put("peaceiris/actions-gh-pages@v3",
                "bd8c6b06eba6b3d73e72aeccb529c056fe8070dd");

        // Slackapi
        map.put("slackapi/slack-github-action@v1",
                "70cd7be8e40a46e8b0eced40b0de447bdb42f68e");

        // Official GitHub actions (used when pinOfficialActions is true)
        map.put("actions/checkout@v4",
                "b4ffde65f46336ab88eb53be808477a3936bae11");
        map.put("actions/checkout@v3",
                "f43a0e5ff2bd294095638e18286ca9a3d1956744");
        map.put("actions/setup-node@v4",
                "60edb5dd545a775178f52524783378180af0d1f8");
        map.put("actions/setup-java@v4",
                "99b8673ff64fbf99d8d325f52d9a5bdedb8483e9");
        map.put("actions/setup-python@v5",
                "0a5c61591373683505ea898e09a3ea4f39ef2b9c");
        map.put("actions/setup-go@v5",
                "cdcb36043654635271a94b9a6d1392de5bb323a7");
        map.put("actions/cache@v4",
                "0c45773b623bea8c8e75f6c82b208c3cf94d9d67");
        map.put("actions/cache@v3",
                "704facf57e6136b1bc63b828d79edcd491f0ee84");
        map.put("actions/upload-artifact@v4",
                "5d5d22a31266ced268874388b861e4b58bb5c2f3");
        map.put("actions/download-artifact@v4",
                "c850b930e6ba138125429b7e5c93fc707a7f8427");
        map.put("actions/github-script@v7",
                "60a0d83039c74a4aee543508d2ffcb1c3799cdea");
        map.put("actions/labeler@v5",
                "8558fd74291d67161a8a78ce36a881fa63b766a9");
        map.put("github/codeql-action/init@v3",
                "1b1aada464948af03b950897e5eb522f92603cc2");
        map.put("github/codeql-action/autobuild@v3",
                "1b1aada464948af03b950897e5eb522f92603cc2");
        map.put("github/codeql-action/analyze@v3",
                "1b1aada464948af03b950897e5eb522f92603cc2");

        return Collections.unmodifiableMap(map);
    }

    private static class PinActionsVisitor extends YamlIsoVisitor<ExecutionContext> {

        private final boolean pinOfficial;
        private final @Nullable String apiToken;

        PinActionsVisitor(boolean pinOfficial, @Nullable String apiToken) {
            this.pinOfficial = pinOfficial;
            this.apiToken = apiToken;
        }

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);

            if (!isUsesKey(e)) {
                return e;
            }

            String usesValue = scalarValue(e.getValue());
            if (usesValue == null) {
                return e;
            }

            // Skip local actions and docker references
            if (usesValue.startsWith("./") || usesValue.startsWith("docker://")) {
                return e;
            }

            Matcher m = USES_PATTERN.matcher(usesValue);
            if (!m.matches()) {
                return e;
            }

            String actionPath = m.group(1);   // e.g. "actions/checkout" or "owner/repo/subpath"
            String ref = m.group(2);           // e.g. "v4" or "main"

            // Already pinned to a SHA
            if (SHA_PATTERN.matcher(ref).matches()) {
                return e;
            }

            // Determine the org from the action path
            String org = actionPath.contains("/") ? actionPath.substring(0, actionPath.indexOf('/')) : actionPath;

            // Skip official actions unless opted in
            if (!pinOfficial && OFFICIAL_ORGS.contains(org)) {
                return e;
            }

            // Resolve SHA: static map first, then API fallback
            String sha = resolveToSha(actionPath, ref, ctx);
            if (sha == null) {
                return e;
            }

            // Build the new value: "owner/repo@<sha>"
            String newValue = actionPath + "@" + sha;

            // Replace the scalar value
            Yaml.Scalar originalScalar = (Yaml.Scalar) e.getValue();
            Yaml.Scalar newScalar = originalScalar.withValue(newValue);

            // Add the original tag as an inline comment for readability.
            // Convention: uses: owner/repo@<sha> # <original-ref>
            //
            // TODO: The inline comment approach depends on the YAML LST's handling
            // of trailing comments. If Yaml.Scalar supports a suffix/comment field,
            // use that. Otherwise, use AddOrUpdateComment or a Yaml.Comment marker.
            // For now, we use the Yaml.Comment approach via the Markers API.
            newScalar = addTrailingComment(newScalar, ref);

            return e.withValue(newScalar);
        }

        /**
         * Resolves an action reference to a commit SHA.
         * Checks the static mapping first; if not found, queries the GitHub API.
         */
        private @Nullable String resolveToSha(String actionPath, String ref, ExecutionContext ctx) {
            String key = actionPath + "@" + ref;

            // 1. Check static map
            String sha = KNOWN_SHAS.get(key);
            if (sha != null) {
                return sha;
            }

            // 2. Fall back to GitHub API
            return resolveViaGitHubApi(actionPath, ref, ctx);
        }

        /**
         * Queries the GitHub API to resolve a git ref (tag or branch) to its commit SHA.
         * Uses the repos/{owner}/{repo}/commits/{ref} endpoint.
         */
        private @Nullable String resolveViaGitHubApi(String actionPath, String ref, ExecutionContext ctx) {
            // Extract owner/repo from the action path (strip subpath if present)
            String ownerRepo = actionPath;
            int thirdSlash = ownerRepo.indexOf('/', ownerRepo.indexOf('/') + 1);
            if (thirdSlash > 0) {
                ownerRepo = ownerRepo.substring(0, thirdSlash);
            }

            String apiUrl = "https://api.github.com/repos/" + ownerRepo + "/commits/" + ref;

            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

                if (apiToken != null && !apiToken.isBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiToken);
                }

                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);

                if (conn.getResponseCode() != 200) {
                    return null;
                }

                try (InputStream is = conn.getInputStream()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(body);
                    String sha = node.path("sha").asText(null);

                    // Validate that it looks like a SHA
                    if (sha != null && SHA_PATTERN.matcher(sha).matches()) {
                        return sha;
                    }
                }
            } catch (IOException e) {
                // Silently skip actions we can't resolve — don't break the build
            }

            return null;
        }

        /**
         * Adds a trailing inline comment to a YAML scalar.
         * The comment preserves the original tag/branch ref for human readability.
         */
        private Yaml.Scalar addTrailingComment(Yaml.Scalar scalar, String originalRef) {
            try {
                return scalar.withSuffix(" # " + originalRef);
            } catch (Exception e) {
                return scalar;
            }
        }

        private boolean isUsesKey(Yaml.Mapping.Entry entry) {
            return entry.getKey() instanceof Yaml.Scalar &&
                    "uses".equals(((Yaml.Scalar) entry.getKey()).getValue());
        }

        private @Nullable String scalarValue(Yaml.Block value) {
            if (value instanceof Yaml.Scalar) {
                return ((Yaml.Scalar) value).getValue();
            }
            return null;
        }
    }
}
