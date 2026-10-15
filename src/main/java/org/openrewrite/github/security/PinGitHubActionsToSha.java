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
import org.openrewrite.github.IsGitHubActionsFile;
import org.openrewrite.github.table.UnresolvedActionShas;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class PinGitHubActionsToSha extends ScanningRecipe<Map<String, String>> {

    private static final Pattern SHA_PATTERN = Pattern.compile("^[a-f0-9]{40}$");
    private static final Pattern USES_PATTERN = Pattern.compile("^([^/@]+/[^/@]+(?:/[^@]+)?)@(.+)$");
    private static final Pattern TAG_REF_PATTERN = Pattern.compile("^v?\\d.*");

    /**
     * Official GitHub-maintained action organizations.
     * These are skipped by default unless {@code pinOfficialActions} is set.
     */
    private static final Set<String> OFFICIAL_ORGS = unmodifiableSet(
            new HashSet<>(Arrays.asList("actions", "github")));

    @Option(displayName = "Pin official actions",
            description = "When set to `true`, also pins actions from official GitHub organizations " +
                    "(e.g., `actions/*`, `github/*`). Defaults to `false`, meaning only third-party " +
                    "actions are pinned.",
            required = false)
    @Nullable
    Boolean pinOfficialActions;

    @Option(displayName = "Trusted owners",
            description = "Optional list of trusted owners/organizations, actions that belong to these organizations " +
                    "will not be pinned. This option overrides the 'Included actions' list.",
            required = false,
            example = "gradle")
    @Nullable
    List<String> trustedOwners;

    @Option(displayName = "Included actions",
            description = "Optional allow-list of actions to pin. When provided, only `uses:` references " +
                    "matching one of these patterns are pinned; all other actions are left untouched. " +
                    "Each pattern must match an action in the built-in mapping of known SHAs; it narrows " +
                    "which mapped actions are pinned, and cannot add new ones. " +
                    "Patterns may be `owner/repo` (exact match), `owner/*` (any repo in an org), or " +
                    "`owner/repo/subpath` (exact match including a subpath). When omitted or empty, all " +
                    "third-party actions (and optionally official actions, per `pinOfficialActions`) are " +
                    "pinned.",
            required = false,
            example = "codecov/codecov-action")
    @Nullable
    List<String> includedActions;

    String displayName = "Pin GitHub Actions to commit SHAs";

    String description = "Replaces mutable tag or branch references in GitHub Actions `uses:` declarations with " +
                "immutable commit SHAs, using a built-in mapping of well-known actions. The recipe makes no " +
                "network calls; references not found in the mapping are left unchanged and listed in a data " +
                "table. By default only third-party actions are pinned; set `pinOfficialActions` " +
                "to include actions from the `actions` and `github` organizations. To pin only a " +
                "subset of the mapped actions, set `includedActions`.";

    Set<String> tags = unmodifiableSet( new HashSet<>( Arrays.asList( "github", "actions", "security", "supply-chain" ) ) );

    transient UnresolvedActionShas unresolvedActionShas = new UnresolvedActionShas(this);

    @Override
    public Validated<Object> validate() {
        Validated<Object> validated = super.validate();
        if (includedActions != null) {
            List<String> unmapped = unmappedPatterns(includedActions, loadKnownShas());
            if (!unmapped.isEmpty()) {
                validated = validated.and(Validated.invalid("includedActions", unmapped,
                        "must only list actions covered by the built-in mapping of known SHAs, but found no mapping for: " +
                                String.join(", ", unmapped)));
            }
        }
        return validated;
    }

    @Override
    public Map<String, String> getInitialValue(ExecutionContext ctx) {
        return loadKnownShas();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<String, String> acc) {
        return TreeVisitor.noop();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<String, String> knownShas) {
        boolean pinOfficial = Boolean.TRUE.equals(pinOfficialActions);
        List<String> allowList = includedActions == null ? emptyList() : includedActions;
        return Preconditions.check(
                new IsGitHubActionsFile(),
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

                        if (YamlHelper.isLocalOrDockerUses(usesValue)) {
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
                        if (trustedOwners != null && trustedOwners.contains(org)) {
                            // Do not pin SHA for trusted orgs
                            return null;
                        }
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

                        String sha = knownShas.get(actionPath + "@" + ref);
                        if (sha == null) {
                            unresolvedActionShas.insertRow(ctx, new UnresolvedActionShas.Row(
                                    getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString(),
                                    actionPath,
                                    ref));
                            return null;
                        }

                        // Replace the scalar value, annotate with the most-specific tag for the SHA
                        Yaml.Scalar originalScalar = (Yaml.Scalar) e.getValue();
                        return new PinResult(
                                e.withValue(originalScalar.withValue(actionPath + "@" + sha)),
                                resolveDisplayTag(knownShas, actionPath, sha, ref)
                        );
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

    /**
     * Resolve the comment tag written next to a pinned SHA. The static mapping already lists every
     * version tag of an action, so a moving tag like {@code v4} shares its SHA with the exact patch
     * release it points at (e.g. {@code v4.4.0}). This scans the mapping for tags of the same action
     * resolving to {@code sha} and returns the most-specific one, so a reader sees {@code # v4.4.0}
     * rather than {@code # v4}. Falls back to the user's original ref when no version tag in the map
     * shares the SHA (e.g. a branch reference such as {@code stable}).
     */
    static String resolveDisplayTag(Map<String, String> knownShas, String actionPath, String sha, String originalRef) {
        String prefix = actionPath + "@";
        String best = null;
        for (Map.Entry<String, String> entry : knownShas.entrySet()) {
            if (!entry.getKey().startsWith(prefix) || !sha.equals(entry.getValue())) {
                continue;
            }
            String tag = entry.getKey().substring(prefix.length());
            if (TAG_REF_PATTERN.matcher(tag).matches() && (best == null || isMoreSpecific(tag, best))) {
                best = tag;
            }
        }
        return best != null ? best : formatRefComment(originalRef);
    }

    /**
     * Compare two version tags by specificity: a tag with more version components (e.g. {@code v4.4.0})
     * is more specific than one with fewer ({@code v4}); an equal number of components is broken by the
     * greater tag so the result is stable regardless of map iteration order.
     */
    static boolean isMoreSpecific(String candidate, String current) {
        long candidateDots = candidate.chars().filter(c -> c == '.').count();
        long currentDots = current.chars().filter(c -> c == '.').count();
        if (candidateDots != currentDots) {
            return candidateDots > currentDots;
        }
        return candidate.compareTo(current) > 0;
    }

    static Map<String, String> loadKnownShas() {
        try (InputStream is = PinGitHubActionsToSha.class
                .getResourceAsStream("/META-INF/rewrite/known-action-shas.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                Map<String, String> map = new LinkedHashMap<>();
                for (String key : props.stringPropertyNames()) {
                    map.put(key, props.getProperty(key));
                }
                return unmodifiableMap(map);
            }
        } catch (IOException ignored) {
        }
        return emptyMap();
    }

    private static List<String> unmappedPatterns(List<String> patterns, Map<String, String> knownShas) {
        Set<String> mappedActions = new HashSet<>();
        for (String key : knownShas.keySet()) {
            mappedActions.add(key.substring(0, key.lastIndexOf('@')));
        }
        List<String> unmapped = new ArrayList<>();
        for (String pattern : patterns) {
            if (StringUtils.isBlank( pattern )) {
                continue;
            }
            List<String> single = singletonList(pattern);
            if (mappedActions.stream().noneMatch(action -> matchesAllowList(action, single))) {
                unmapped.add(pattern);
            }
        }
        return unmapped;
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
