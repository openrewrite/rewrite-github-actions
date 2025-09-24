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
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class UnpinnedDockerImagesRecipe extends Recipe {

    private static final Pattern DOCKER_IMAGE_PATTERN = Pattern.compile(
            "^(?:docker://)?([^/:]+(?:\\.[^/:]+)*(?::[0-9]+)?/)?([^/:]+(?:/[^/:]+)*):([^@]+)(?:@(.+))?$"
    );

    private static final Pattern SHA256_DIGEST_PATTERN = Pattern.compile("^sha256:[a-f0-9]{64}$");

    @Override
    public String getDisplayName() {
        return "Pin Docker images to digests";
    }

    @Override
    public String getDescription() {
        return "Pin Docker images to specific digest hashes for security and reproducibility. " +
                "Images pinned to tags can be changed by the image author, " +
                "while digest pins are immutable. " +
                "Based on [zizmor's unpinned-images audit](https://github.com/woodruffw/zizmor/blob/main/crates/zizmor/src/audit/unpinned_images.rs).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles(".github/workflows/*.yml"),
                new UnpinnedDockerImagesVisitor()
        );
    }

    private static class UnpinnedDockerImagesVisitor extends YamlIsoVisitor<ExecutionContext> {

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
            Yaml.Mapping.Entry mappingEntry = super.visitMappingEntry(entry, ctx);

            if (isImageEntry(mappingEntry)) {
                String imageValue = getImageValue(mappingEntry);
                if (imageValue != null && isUnpinnedDockerImage(imageValue)) {
                    return SearchResult.found(mappingEntry,
                            "Docker image '" + imageValue + "' is not pinned to a digest. " +
                                    "Consider pinning to a specific digest for security and reproducibility.");
                }
            }

            return mappingEntry;
        }

        private boolean isImageEntry(Yaml.Mapping.Entry entry) {
            return "image".equals(entry.getKey().getValue());
        }

        private String getImageValue(Yaml.Mapping.Entry entry) {
            return entry.getValue() instanceof Yaml.Scalar ? ((Yaml.Scalar) entry.getValue()).getValue() : null;
        }

        private boolean isUnpinnedDockerImage(String imageValue) {
            // Handle docker:// prefix
            String cleanImage = imageValue;
            if (cleanImage.startsWith("docker://")) {
                cleanImage = cleanImage.substring("docker://".length());
            }

            // Check if it has a digest (@sha256:...)
            if (cleanImage.contains("@")) {
                String[] parts = cleanImage.split("@", 2);
                if (parts.length == 2) {
                    String digest = parts[1];
                    return !SHA256_DIGEST_PATTERN.matcher(digest).matches();
                }
            }

            // If no digest, it's unpinned
            return true;
        }
    }
}
