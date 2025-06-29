// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.config;

import org.apache.commons.cli.Option;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import static org.jetbrains.idea.maven.config.MavenConfigSettings.*;

public class MavenConfig {
    private final Map<String, Option> optionMap;
    private final String baseDir;

    public MavenConfig(@Nonnull Map<String, Option> map, @Nonnull String dir) {
        optionMap = Objects.requireNonNull(map);
        baseDir = Objects.requireNonNull(dir);
    }

    public boolean hasOption(@Nonnull MavenConfigSettings configSetting) {
        return optionMap.containsKey(configSetting.key);
    }

    public String getOptionValue(@Nonnull MavenConfigSettings configSetting) {
        Option option = optionMap.get(configSetting.key);
        return option == null ? null : option.getValue();
    }

    public MavenExecutionOptions.FailureMode getFailureMode() {
        if (hasOption(FAIL_NEVER)) return MavenExecutionOptions.FailureMode.NEVER;
        if (hasOption(FAIL_AT_END)) return MavenExecutionOptions.FailureMode.AT_END;
        if (hasOption(FAIL_FAST)) return MavenExecutionOptions.FailureMode.FAST;
        return null;
    }

    public MavenExecutionOptions.ChecksumPolicy getChecksumPolicy() {
        if (hasOption(CHECKSUM_WARNING_POLICY)) return MavenExecutionOptions.ChecksumPolicy.WARN;
        if (hasOption(CHECKSUM_FAILURE_POLICY)) return MavenExecutionOptions.ChecksumPolicy.FAIL;
        return null;
    }

    public MavenExecutionOptions.LoggingLevel getOutputLevel() {
        if (hasOption(QUIET)) return MavenExecutionOptions.LoggingLevel.DISABLED;
        if (hasOption(DEBUG)) return MavenExecutionOptions.LoggingLevel.DEBUG;
        return null;
    }

    public @Nullable String getFilePath(@Nonnull MavenConfigSettings configSetting) {
        Option option = optionMap.get(configSetting.key);
        if (option == null) return null;

        Path file = Path.of(option.getValue());
        if (file.isAbsolute() && Files.exists(file)) return option.getValue();
        file = Path.of(baseDir, option.getValue());
        if (Files.exists(file)) return file.toAbsolutePath().toString();
        return null;
    }

    public boolean isEmpty() {
        return optionMap.isEmpty();
    }
}
