// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum LogMessageType {
    DEBUG,
    INFO,
    WARNING,
    ERROR;

    private static final Pattern LOG_LEVEL_PATTERN = Pattern.compile("\\[(DEBUG|INFO|WARNING|ERROR)]\\s+");

    @Nonnull
    String clearLine(@Nonnull String line) {
        Matcher matcher = LOG_LEVEL_PATTERN.matcher(line);
        matcher.find();
        return line.substring(matcher.end());
    }

    @Nullable
    static LogMessageType determine(@Nonnull String line) {
        Matcher matcher = LOG_LEVEL_PATTERN.matcher(line);
        return matcher.find() ? LogMessageType.valueOf(matcher.group(1)) : null;
    }
}
