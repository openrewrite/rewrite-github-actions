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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.github.IsGitHubActionsWorkflow;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class PinGitHubActionsToSha extends ScanningRecipe<Map<String, String>> {

    private static final Pattern SHA_PATTERN = Pattern.compile("^[a-f0-9]{40}$");
    private static final Pattern USES_PATTERN = Pattern.compile("^([^/@]+/[^/@]+(?:/[^@]+)?)@(.+)$");
    private static final Pattern SHA_RESPONSE_PATTERN = Pattern.compile("\"sha\"\\s*:\\s*\"([a-f0-9]{40})\"");

    /**
     * Official GitHub-maintained action organizations.
     * These are skipped by default unless {@code pinOfficialActions} is set.
     */
    private static final Set<String> OFFICIAL_ORGS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("actions", "github")));

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
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList("github", "actions", "security", "supply-chain")));
    }

    @Override
    public Map<String, String> getInitialValue(ExecutionContext ctx) {
        try (InputStream is = PinGitHubActionsToSha.class
                .getResourceAsStream("/META-INF/rewrite/known-action-shas.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                Map<String, String> map = new LinkedHashMap<>();
                for (String key : props.stringPropertyNames()) {
                    map.put(key, props.getProperty(key));
                }
                return Collections.unmodifiableMap(map);
            }
        } catch (IOException ignored) {
        }
        return Collections.emptyMap();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<String, String> acc) {
        return TreeVisitor.noop();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<String, String> knownShas) {
        boolean pinOfficial = Boolean.TRUE.equals(pinOfficialActions);
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new PinActionsVisitor(pinOfficial, githubApiToken, knownShas)
        );
    }

    private static class PinActionsVisitor extends YamlIsoVisitor<ExecutionContext> {

        private final boolean pinOfficial;
        private final @Nullable String apiToken;
        private final Map<String, String> knownShas;

        PinActionsVisitor(boolean pinOfficial, @Nullable String apiToken, Map<String, String> knownShas) {
            this.pinOfficial = pinOfficial;
            this.apiToken = apiToken;
            this.knownShas = knownShas;
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

            return e.withValue(newScalar);
        }

        /**
         * Resolves an action reference to a commit SHA.
         * Checks the static mapping first; if not found, queries the GitHub API.
         */
        private @Nullable String resolveToSha(String actionPath, String ref, ExecutionContext ctx) {
            String key = actionPath + "@" + ref;

            // 1. Check known SHAs map
            String sha = knownShas.get(key);
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

                if (apiToken != null && !apiToken.trim().isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiToken);
                }

                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);

                if (conn.getResponseCode() != 200) {
                    return null;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    Matcher shaMatcher = SHA_RESPONSE_PATTERN.matcher(sb.toString());
                    if (shaMatcher.find()) {
                        String sha = shaMatcher.group(1);
                        if (SHA_PATTERN.matcher(sha).matches()) {
                            return sha;
                        }
                    }
                }
            } catch (IOException e) {
                // Silently skip actions we can't resolve — don't break the build
            }

            return null;
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
