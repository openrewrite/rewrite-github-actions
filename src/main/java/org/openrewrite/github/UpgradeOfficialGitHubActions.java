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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableSet;

@Value
@EqualsAndHashCode(callSuper = false)
public class UpgradeOfficialGitHubActions extends ScanningRecipe<UpgradeOfficialGitHubActions.Accumulator> {

    String displayName = "Upgrade official GitHub Actions to their latest versions";

    String description = "Upgrades actions from the official `actions` and `github` organizations to the newest " +
            "known version, working entirely offline. Each reference is upgraded while preserving its existing " +
            "precision: a major version (`v4`) moves to the newest major, a full version (`v4.1.2`) to the newest " +
            "full version, and a commit SHA to the latest known commit. Actions that are not official, not known, " +
            "or already up to date are left untouched.";

    Set<String> tags = unmodifiableSet(new HashSet<>(asList("github", "actions")));

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(OfficialActionVersions.fromProperties(loadKnownShas()));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return Preconditions.check(new IsGitHubActionsWorkflow(), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                if (entry.getKey() instanceof Yaml.Scalar &&
                        "uses".equals(((Yaml.Scalar) entry.getKey()).getValue()) &&
                        entry.getValue() instanceof Yaml.Scalar) {
                    String uses = ((Yaml.Scalar) entry.getValue()).getValue();
                    int at = uses.indexOf('@');
                    if (at > 0) {
                        String action = uses.substring(0, at);
                        String currentRef = uses.substring(at + 1);
                        String target = acc.getVersions().upgrade(action, currentRef);
                        if (target != null) {
                            acc.getTargets().add(new UpgradeTarget(action, currentRef, target));
                        }
                    }
                }
                return super.visitMappingEntry(entry, ctx);
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (acc.getTargets().isEmpty()) {
            return TreeVisitor.noop();
        }
        return Preconditions.check(new IsGitHubActionsWorkflow(), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                for (UpgradeTarget target : acc.getTargets()) {
                    doAfterVisit(new ChangeActionVersion(target.getAction(), target.getTarget(), target.getCurrentRef())
                            .getVisitor());
                }
                return documents;
            }
        });
    }

    private static Map<String, String> loadKnownShas() {
        try (InputStream is = UpgradeOfficialGitHubActions.class
                .getResourceAsStream("/META-INF/rewrite/known-action-shas.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                Map<String, String> map = new LinkedHashMap<>();
                for (String key : props.stringPropertyNames()) {
                    map.put(key, props.getProperty(key));
                }
                return map;
            }
        } catch (IOException ignored) {
        }
        return emptyMap();
    }

    @Value
    public static class Accumulator {
        OfficialActionVersions versions;
        Set<UpgradeTarget> targets = new LinkedHashSet<>();
    }

    @Value
    public static class UpgradeTarget {
        String action;
        String currentRef;
        String target;
    }
}
