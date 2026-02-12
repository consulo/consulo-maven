// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import consulo.maven.rt.server.common.server.MavenArtifactEvent;
import consulo.maven.rt.server.common.server.MavenServerConsoleEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface MavenEventHandler {
    void handleConsoleEvents(@NotNull List<MavenServerConsoleEvent> consoleEvents);

    void handleDownloadEvents(@NotNull List<MavenArtifactEvent> downloadEvents);
}
