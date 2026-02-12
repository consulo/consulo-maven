// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.issue.BuildIssue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface MavenBuildIssueHandler {
    void addBuildIssue(@Nonnull BuildIssue issue, @Nonnull MessageEvent.Kind kind);
}
