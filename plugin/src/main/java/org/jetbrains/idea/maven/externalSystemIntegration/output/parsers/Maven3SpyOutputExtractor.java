// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import jakarta.annotation.Nullable;

public class Maven3SpyOutputExtractor implements SpyOutputExtractor {
    @Override
    public boolean isSpyLog(@Nullable String s) {
        return s != null && s.startsWith(MavenSpyOutputParser.PREFIX_MAVEN_3);
    }

    @Override
    @Nullable
    public String extract(String line) {
        if (line.startsWith(MavenSpyOutputParser.PREFIX_MAVEN_3)) {
            return line.substring(MavenSpyOutputParser.PREFIX_MAVEN_3.length());
        }
        return null;
    }

    @Override
    public boolean isLengthEnough(String s) {
        return s.length() >= MavenSpyOutputParser.PREFIX_MAVEN_3.length();
    }
}
