// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import jakarta.annotation.Nullable;

public interface SpyOutputExtractor {
    boolean isSpyLog(@Nullable String s);

    @Nullable
    String extract(String spyLine);

    boolean isLengthEnough(String s);
}
