package org.openrewrite.github;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.yaml.InsertYaml;

@EqualsAndHashCode(callSuper = false)
@Getter
public class AddCronTrigger extends Recipe {

    @Option(displayName = "Cron expression",
            description = "Using the [POSIX cron syntax](https://pubs.opengroup.org/onlinepubs/9699919799/utilities/crontab.html#tag_20_25_07).",
            example = "0 18 * * *")
    private final String cron;

    public AddCronTrigger(String cron) {
        this.cron = cron;
        doNext(new InsertYaml(
                "/on",
                "" +
                        "schedule:\n" +
                        "  - cron: \"0 18 * * *\"",
                ".github/workflows/*.yml")
        );
    }

    @Override
    public String getDisplayName() {
        return "Add cron workflow trigger";
    }

    @Override
    public String getDescription() {
        return "The `schedule` [event](https://docs.github.com/en/actions/reference/events-that-trigger-workflows#scheduled-events) allows you to trigger a workflow at a scheduled time.";
    }
}
