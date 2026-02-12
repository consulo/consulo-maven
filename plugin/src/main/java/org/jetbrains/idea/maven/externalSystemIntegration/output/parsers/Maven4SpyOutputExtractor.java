// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import jakarta.annotation.Nullable;

import java.util.regex.Pattern;

public class Maven4SpyOutputExtractor implements SpyOutputExtractor {
    private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\u001B\\[[;\\d]*m");

    @Override
    public boolean isSpyLog(@Nullable String s) {
        if (s == null) {
            return false;
        }
        String cleared = ANSI_ESCAPE_PATTERN.matcher(s).replaceAll("");
        return cleared.startsWith(MavenSpyOutputParser.PREFIX_MAVEN_4);
    }

    @Override
    @Nullable
    public String extract(String line) {
        String clearedLine = ANSI_ESCAPE_PATTERN.matcher(line).replaceAll("");
        if (clearedLine.startsWith(MavenSpyOutputParser.PREFIX_MAVEN_4)) {
            return clearedLine.substring(MavenSpyOutputParser.PREFIX_MAVEN_4.length());
        }
        return null;
    }

    @Override
    public boolean isLengthEnough(String s) {
        String cleared = ANSI_ESCAPE_PATTERN.matcher(s).replaceAll("");
        return cleared.length() >= MavenSpyOutputParser.PREFIX_MAVEN_4.length();
    }
}
