// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import consulo.build.ui.event.MessageEvent;
import consulo.build.ui.issue.BuildIssue;
import jakarta.annotation.Nonnull;

public interface MavenBuildIssueHandler {
    void addBuildIssue(@Nonnull BuildIssue issue, @Nonnull MessageEvent.Kind kind);
}
