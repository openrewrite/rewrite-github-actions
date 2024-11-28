package org.openrewrite.github;

import javax.annotation.Nullable;
import java.util.Optional;

public class UseGradleWrapperAccumulator {
    @Nullable Boolean gradlewExists = null;
    Optional<String> newExecutableName = Optional.empty();
}
