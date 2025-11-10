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
import org.openrewrite.*;
import org.openrewrite.github.IsGitHubActionsWorkflow;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class RefVersionMismatchRecipe extends Recipe {

    private static final Pattern SHA_PATTERN = Pattern.compile("^[a-f0-9]{40}$");

    // Version comment patterns - matches various comment formats
    private static final Pattern[] VERSION_COMMENT_PATTERNS = {
            Pattern.compile("#\\s*tag\\s*=\\s*(v?\\d+(?:\\.\\d+)*(?:\\.\\d+)?)"),
            Pattern.compile("#\\s*(v?\\d+(?:\\.\\d+)*(?:\\.\\d+)?)\\s*$"),
            Pattern.compile("#\\s*(?:version|ver)\\s*[:=]\\s*(v?\\d+(?:\\.\\d+)*(?:\\.\\d+)?)"),
            Pattern.compile("#\\s*tag\\s*=\\s*([vV]?\\d+)"),
    };

    @Override
    public String getDisplayName() {
        return "Find commit SHAs with potentially mismatched version comments";
    }

    @Override
    public String getDescription() {
        return "Find GitHub Actions that are pinned to commit SHAs but have version comments that may not match the actual pinned version. " +
                "This can lead to confusion about which version is actually being used and potential security issues if the comment " +
                "misleads developers about the pinned version. " +
                "Based on [zizmor's `ref-version-mismatch` audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/ref_version_mismatch.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new IsGitHubActionsWorkflow(),
                new RefVersionMismatchVisitor()
        );
    }

    private static class RefVersionMismatchVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            if (isUsesEntry(mappingEntry)) {
                return checkUsesEntry(mappingEntry);
            }

            return mappingEntry;
        }

        private boolean isUsesEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getKey() instanceof Yaml.Scalar)) {
                return false;
            }
            Yaml.Scalar key = (Yaml.Scalar) entry.getKey();
            return "uses".equals(key.getValue());
        }

        private Yaml.Mapping.Entry checkUsesEntry(Yaml.Mapping.Entry entry) {
            if (!(entry.getValue() instanceof Yaml.Scalar)) {
                return entry;
            }

            String usesValue = ((Yaml.Scalar) entry.getValue()).getValue();

            // Only check repository actions (not local or docker actions)
            if (usesValue.startsWith("./") || usesValue.startsWith("docker://")) {
                return entry;
            }

            // Check if it's pinned to a commit SHA
            String[] parts = usesValue.split("@", 2);
            if (parts.length < 2) {
                return entry; // No version specified
            }

            String version = parts[1];
            if (!SHA_PATTERN.matcher(version).matches()) {
                return entry; // Not a commit SHA
            }

            // Look for version comments in the surrounding context
            if (hasVersionComment(entry)) {
                return SearchResult.found(entry,
                        "Action is pinned to a commit SHA but has a version comment that may not match. " +
                                "Verify the comment reflects the actual pinned version.");
            }

            return entry;
        }

        private boolean hasVersionComment(Yaml.Mapping.Entry entry) {
            // Check for version comments in the current line or preceding lines

            // Look at the entry's prefix for inline comments
            String prefix = entry.getPrefix();
            if (prefix != null && containsVersionComment(prefix)) {
                return true;
            }

            // Check if there are preceding comments in the same step
            return checkPrecedingComments(entry);
        }

        private boolean containsVersionComment(String text) {
            for (Pattern pattern : VERSION_COMMENT_PATTERNS) {
                if (pattern.matcher(text).find()) {
                    return true;
                }
            }
            return false;
        }

        private boolean checkPrecedingComments(Yaml.Mapping.Entry entry) {
            // In OpenRewrite, we can check the source lines around this entry
            // This is a simplified version - in practice, you'd need to check
            // the actual source text and comments more carefully

            // Check if we're in a sequence entry (step) that might have comments
            Cursor current = getCursor();
            while (current != null) {
                Object value = current.getValue();
                if (value instanceof Yaml.Sequence.Entry) {
                    Yaml.Sequence.Entry seqEntry = (Yaml.Sequence.Entry) value;
                    String seqPrefix = seqEntry.getPrefix();
                    if (seqPrefix != null && containsVersionComment(seqPrefix)) {
                        return true;
                    }
                }
                current = current.getParent();
            }

            return false;
        }
    }
}
