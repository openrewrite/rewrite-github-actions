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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.github.IsGitHubActionsWorkflow;
import org.openrewrite.ipc.http.HttpSender;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class PinGitHubActionsToSha extends ScanningRecipe<Map<String, String>> {

    private static final Pattern SHA_PATTERN = Pattern.compile("^[a-f0-9]{40}$");
    private static final Pattern USES_PATTERN = Pattern.compile("^([^/@]+/[^/@]+(?:/[^@]+)?)@(.+)$");
    private static final Pattern SHA_RESPONSE_PATTERN = Pattern.compile("\\A\\s*\\{\\s*\"sha\"\\s*:\\s*\"([a-f0-9]{40})\"");
    private static final Pattern TAG_REF_PATTERN = Pattern.compile("^v?\\d.*");

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

    @Option(displayName = "Included actions",
            description = "Optional allow-list of actions to pin. When provided, only `uses:` references " +
                    "matching one of these patterns are pinned; all other actions are left untouched. " +
                    "Patterns may be `owner/repo` (exact match), `owner/*` (any repo in an org), or " +
                    "`owner/repo/subpath` (exact match including a subpath). When omitted or empty, all " +
                    "third-party actions (and optionally official actions, per `pinOfficialActions`) are " +
                    "pinned.",
            required = false,
            example = "codecov/codecov-action")
    @Nullable
    List<String> includedActions;

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
                "to include actions from the `actions` and `github` organizations. To pin only a " +
                "specific allow-list of actions, set `includedActions`.";
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
        String apiToken = githubApiToken;
        List<String> allowList = includedActions == null ? Collections.emptyList() : includedActions;
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new YamlIsoVisitor<ExecutionContext>() {

                    /**
                     * When a {@code uses:} entry is pinned, the formatted ref comment is stashed
                     * here so the very next syntactic node visited (in document order) can have it
                     * merged into its prefix as a {@code # vX} marker on the same line as the
                     * pinned value. Consumed and cleared by whichever of {@link #visitMappingEntry},
                     * {@link #visitSequenceEntry}, or {@link #visitDocumentEnd} fires next.
                     */
                    @Nullable
                    String pendingComment;

                    @Override
                    public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                        pendingComment = null;
                        return super.visitDocuments(documents, ctx);
                    }

                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        if (pendingComment != null) {
                            entry = entry.withPrefix(mergeTagCommentIntoPrefix(entry.getPrefix(), pendingComment));
                            pendingComment = null;
                        }
                        PinResult result = pinEntry(entry, ctx);
                        if (result != null) {
                            entry = result.entry;
                            pendingComment = result.commentText;
                        }
                        return super.visitMappingEntry(entry, ctx);
                    }

                    @Override
                    public Yaml.Sequence.Entry visitSequenceEntry(Yaml.Sequence.Entry entry, ExecutionContext ctx) {
                        if (pendingComment != null) {
                            entry = entry.withPrefix(mergeTagCommentIntoPrefix(entry.getPrefix(), pendingComment));
                            pendingComment = null;
                        }
                        return super.visitSequenceEntry(entry, ctx);
                    }

                    @Override
                    public Yaml.Document.End visitDocumentEnd(Yaml.Document.End end, ExecutionContext ctx) {
                        if (pendingComment != null) {
                            end = end.withPrefix(mergeTagCommentIntoPrefix(end.getPrefix(), pendingComment));
                            pendingComment = null;
                        }
                        return super.visitDocumentEnd(end, ctx);
                    }

                    private @Nullable PinResult pinEntry(Yaml.Mapping.Entry e, ExecutionContext ctx) {
                        if (!(e.getKey() instanceof Yaml.Scalar) ||
                                !"uses".equals(((Yaml.Scalar) e.getKey()).getValue())) {
                            return null;
                        }

                        if (!(e.getValue() instanceof Yaml.Scalar)) {
                            return null;
                        }
                        String usesValue = ((Yaml.Scalar) e.getValue()).getValue();

                        // Skip local actions and docker references
                        if (usesValue.startsWith("./") || usesValue.startsWith("docker://")) {
                            return null;
                        }

                        Matcher m = USES_PATTERN.matcher(usesValue);
                        if (!m.matches()) {
                            return null;
                        }

                        String actionPath = m.group(1);   // e.g. "actions/checkout" or "owner/repo/subpath"
                        String ref = m.group(2);           // e.g. "v4" or "main"

                        // Already pinned to a SHA
                        if (SHA_PATTERN.matcher(ref).matches()) {
                            return null;
                        }

                        // Determine the org from the action path
                        String org = actionPath.contains("/") ? actionPath.substring(0, actionPath.indexOf('/')) : actionPath;

                        if (!allowList.isEmpty()) {
                            // Allow-list mode: only pin actions matching an entry in the list.
                            // pinOfficialActions is bypassed — explicit allow always wins.
                            if (!matchesAllowList(actionPath, allowList)) {
                                return null;
                            }
                        } else if (!pinOfficial && OFFICIAL_ORGS.contains(org)) {
                            // Default mode: skip official actions unless opted in
                            return null;
                        }

                        // Resolve SHA: static map first, then API fallback
                        String sha = resolveToSha(actionPath, ref, ctx);
                        if (sha == null) {
                            return null;
                        }

                        // Replace the scalar value, retain the original ref as a comment
                        Yaml.Scalar originalScalar = (Yaml.Scalar) e.getValue();
                        return new PinResult(
                                e.withValue(originalScalar.withValue(actionPath + "@" + sha)),
                                formatRefComment(ref)
                        );
                    }

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

                    private @Nullable String resolveViaGitHubApi(String actionPath, String ref, ExecutionContext ctx) {
                        // Extract owner/repo from the action path (strip subpath if present)
                        String ownerRepo = actionPath;
                        int thirdSlash = ownerRepo.indexOf('/', ownerRepo.indexOf('/') + 1);
                        if (thirdSlash > 0) {
                            ownerRepo = ownerRepo.substring(0, thirdSlash);
                        }

                        String apiUrl = "https://api.github.com/repos/" + ownerRepo + "/commits/" + ref;

                        HttpSender httpSender = HttpSenderExecutionContextView.view(ctx).getHttpSender();
                        HttpSender.Request.Builder request = httpSender.get(apiUrl)
                                .withHeader("Accept", "application/vnd.github+json")
                                .withHeader("X-GitHub-Api-Version", "2022-11-28")
                                .withAuthentication("Bearer", apiToken);

                        try (HttpSender.Response response = httpSender.send(request.build())) {
                            if (!response.isSuccessful()) {
                                return null;
                            }
                            String responseBody = new String(response.getBodyAsBytes(), StandardCharsets.UTF_8);
                            Matcher shaMatcher = SHA_RESPONSE_PATTERN.matcher(responseBody);
                            if (shaMatcher.find()) {
                                String sha = shaMatcher.group(1);
                                if (SHA_PATTERN.matcher(sha).matches()) {
                                    return sha;
                                }
                            }
                        } catch (RuntimeException e) {
                            // Silently skip actions we can't resolve — don't break the build
                        }

                        return null;
                    }
                }
        );
    }

    /**
     * Insert a {@code # <commentText>} marker onto the line of the pinned {@code uses:} value,
     * replacing any pre-existing inline comment on that line. The comment lives in the prefix of
     * the next sibling node (or the document end), preceded by whatever same-line whitespace +
     * comment the prefix may already start with. Anything after the first newline is preserved
     * unchanged so the indentation of the next entry stays intact.
     *
     * <p>Replacing rather than appending matches the convention used by Dependabot and Renovate,
     * which read the first {@code #} comment after the SHA as the version tag for future updates.
     */
    static String mergeTagCommentIntoPrefix(String existingPrefix, String commentText) {
        String formatted = " # " + commentText;
        int newlineIdx = existingPrefix.indexOf('\n');
        String onSameLine = newlineIdx < 0 ? existingPrefix : existingPrefix.substring(0, newlineIdx);
        if (onSameLine.contains("#")) {
            String afterNewline = newlineIdx < 0 ? "" : existingPrefix.substring(newlineIdx);
            return formatted + afterNewline;
        }
        return formatted + existingPrefix;
    }

    /**
     * Format the comment text that will be written next to the pinned SHA. Tag-like refs
     * (anything starting with {@code v?\d}, e.g. {@code v4}, {@code v1.2.3}, {@code 2.1.0}) are
     * emitted as-is so they remain stable identifiers for tooling like Dependabot and Renovate.
     * Branch-like refs (e.g. {@code main}, {@code master}, {@code develop}) are stamped with the
     * resolution date in UTC, since the SHA they point at can change at any time.
     */
    static String formatRefComment(String ref) {
        if (TAG_REF_PATTERN.matcher(ref).matches()) {
            return ref;
        }
        return ref + " @ " + LocalDate.now(ZoneOffset.UTC);
    }

    private static boolean matchesAllowList(String actionPath, List<String> allowList) {
        // actionPath may be "owner/repo" or "owner/repo/subpath".
        int firstSlash = actionPath.indexOf('/');
        String owner = firstSlash > 0 ? actionPath.substring(0, firstSlash) : actionPath;
        int secondSlash = actionPath.indexOf('/', firstSlash + 1);
        String ownerRepo = secondSlash > 0 ? actionPath.substring(0, secondSlash) : actionPath;

        for (String pattern : allowList) {
            if (pattern == null) {
                continue;
            }
            String p = pattern.trim();
            if (p.isEmpty()) {
                continue;
            }
            // owner/* — match any repo within the org
            if (p.endsWith("/*")) {
                String allowedOwner = p.substring(0, p.length() - 2);
                if (allowedOwner.equals(owner)) {
                    return true;
                }
                continue;
            }
            // owner/repo/subpath — exact match against the full path
            if (p.indexOf('/') != p.lastIndexOf('/')) {
                if (p.equals(actionPath)) {
                    return true;
                }
                continue;
            }
            // owner/repo — match against owner/repo, ignoring any subpath on the action
            if (p.equals(ownerRepo)) {
                return true;
            }
        }
        return false;
    }

    private static class PinResult {
        final Yaml.Mapping.Entry entry;
        final String commentText;

        PinResult(Yaml.Mapping.Entry entry, String commentText) {
            this.entry = entry;
            this.commentText = commentText;
        }
    }
}
