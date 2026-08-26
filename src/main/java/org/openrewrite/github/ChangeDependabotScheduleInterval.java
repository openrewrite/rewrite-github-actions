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

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeDependabotScheduleInterval extends Recipe {
    @Option(displayName = "Package ecosystem",
            description = "The package-ecosystem to make updates on.",
            example = "maven")
    String packageEcosystem;

    @Option(displayName = "Schedule interval",
            description = "The schedule interval value the package-ecosystem should use.",
            valid = {"daily", "weekly", "monthly"},
            example = "weekly")
    String interval;

    @Option(displayName = "Schedule day",
            description = "The day of the week to run updates when the schedule interval is `weekly`.",
            example = "monday",
            required = false)
    @Nullable
    String day;

    @Option(displayName = "Schedule time",
            description = "The time of day to run updates, in `HH:mm` format. Defaults to UTC unless `timezone` is set.",
            example = "09:00",
            required = false)
    @Nullable
    String time;

    @Option(displayName = "Schedule timezone",
            description = "The IANA time zone identifier for the configured schedule time.",
            example = "Asia/Tokyo",
            required = false)
    @Nullable
    String timezone;

    public ChangeDependabotScheduleInterval(String packageEcosystem, String interval) {
        this(packageEcosystem, interval, null, null, null);
    }

    @JsonCreator
    public ChangeDependabotScheduleInterval(String packageEcosystem, String interval, @Nullable String day,
                                            @Nullable String time, @Nullable String timezone) {
        this.packageEcosystem = packageEcosystem;
        this.interval = interval;
        this.day = day;
        this.time = time;
        this.timezone = timezone;
    }

    String displayName = "Change dependabot schedule interval";

    String description = "Change the schedule interval and optionally the day, time, and time zone for a given " +
                "package-ecosystem in a `dependabot.yml` configuration file. " +
                "[The available configuration options for dependabot are listed on GitHub](https://docs.github.com/en/code-security/supply-chain-security/keeping-your-dependencies-updated-automatically/configuration-options-for-dependency-updates).";

    @Override
    public Set<String> getTags() {
        Set<String> tags = new HashSet<>();
        tags.add("dependabot");
        tags.add("dependencies");
        tags.add("github");
        return tags;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".github/dependabot.{yml,yaml}"), new YamlIsoVisitor<ExecutionContext>() {
            private static final String CONFIGURE_SCHEDULE = "CONFIGURE_SCHEDULE";
            private final JsonPathMatcher packageEcosystemMatcher =
                    new JsonPathMatcher("$.updates[*].package-ecosystem");
            private final Pattern packageEcosystemPattern = Pattern.compile(packageEcosystem);

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                if (packageEcosystemMatcher.matches(getCursor()) && e.getValue() instanceof Yaml.Scalar &&
                        packageEcosystemPattern.matcher(((Yaml.Scalar) e.getValue()).getValue()).matches()) {
                    getCursor().dropParentUntil(Yaml.Mapping.class::isInstance).putMessage(CONFIGURE_SCHEDULE, true);
                }
                return e;
            }

            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                Yaml.Mapping m = super.visitMapping(mapping, ctx);
                if (!Boolean.TRUE.equals(getCursor().pollMessage(CONFIGURE_SCHEDULE))) {
                    return m;
                }
                return m.withEntries(ListUtils.map(m.getEntries(), entry -> {
                    if (!"schedule".equals(entry.getKey().getValue()) ||
                            !(entry.getValue() instanceof Yaml.Mapping)) {
                        return entry;
                    }
                    Cursor scheduleEntryCursor = new Cursor(getCursor(), entry);
                    return entry.withValue(configureSchedule(
                            (Yaml.Mapping) entry.getValue(), ctx, scheduleEntryCursor));
                }));
            }

            private Yaml.Mapping configureSchedule(Yaml.Mapping schedule, ExecutionContext ctx,
                                                   Cursor scheduleEntryCursor) {
                Yaml.Mapping m = upsert(schedule, "interval", interval, false, ctx, scheduleEntryCursor);
                if (day != null) {
                    m = upsert(m, "day", day, false, ctx, scheduleEntryCursor);
                }
                if (time != null) {
                    m = upsert(m, "time", time, true, ctx, scheduleEntryCursor);
                }
                if (timezone != null) {
                    m = upsert(m, "timezone", timezone, true, ctx, scheduleEntryCursor);
                }
                return m;
            }

            private Yaml.Mapping upsert(Yaml.Mapping schedule, String key, String value, boolean quoted,
                                        ExecutionContext ctx, Cursor scheduleEntryCursor) {
                for (Yaml.Mapping.Entry entry : schedule.getEntries()) {
                    if (key.equals(entry.getKey().getValue())) {
                        if (entry.getValue() instanceof Yaml.Scalar &&
                                !value.equals(((Yaml.Scalar) entry.getValue()).getValue())) {
                            return schedule.withEntries(ListUtils.map(schedule.getEntries(), e -> e == entry ?
                                    e.withValue(((Yaml.Scalar) e.getValue()).withValue(value)) : e));
                        }
                        return schedule;
                    }
                }

                Yaml.Mapping.Entry newEntry = new YamlParser()
                        .parse(ctx, key + ": " + (quoted ? quote(value) : value))
                        .map(Yaml.Documents.class::cast)
                        .map(documents -> (Yaml.Mapping) documents.getDocuments().get(0).getBlock())
                        .map(parsed -> parsed.getEntries().get(0))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Failed to parse Dependabot schedule option"));
                if (schedule.getOpeningBracePrefix() != null) {
                    schedule = schedule.withOpeningBracePrefix(null).withClosingBracePrefix(null);
                    schedule = schedule.withEntries(ListUtils.mapFirst(schedule.getEntries(),
                            first -> first.withPrefix("\n")));
                    schedule = autoFormat(schedule, ctx, scheduleEntryCursor);
                }
                Cursor scheduleCursor = new Cursor(scheduleEntryCursor, schedule);
                newEntry = autoFormat(newEntry, ctx, scheduleCursor);

                List<Yaml.Mapping.Entry> entries = schedule.getEntries();
                int keyOrder = scheduleKeyOrder(key);
                int insertionIndex = entries.size();
                for (int i = 0; i < entries.size(); i++) {
                    if (scheduleKeyOrder(entries.get(i).getKey().getValue()) > keyOrder) {
                        insertionIndex = i;
                        break;
                    }
                }
                return schedule.withEntries(ListUtils.insert(entries, newEntry, insertionIndex));
            }

            private int scheduleKeyOrder(String key) {
                switch (key) {
                    case "interval":
                        return 0;
                    case "day":
                        return 1;
                    case "time":
                        return 2;
                    case "timezone":
                        return 3;
                    default:
                        return Integer.MAX_VALUE;
                }
            }

            private String quote(String value) {
                return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
            }
        });
    }

}
