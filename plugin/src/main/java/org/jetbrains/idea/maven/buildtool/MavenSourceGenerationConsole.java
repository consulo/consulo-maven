// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import consulo.application.Application;
import consulo.build.ui.event.BuildEventFactory;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.execution.RunnerBundle;

/**
 * An instance of this class is not supposed to be reused
 */
public class MavenSourceGenerationConsole extends MavenSyncConsoleBase {

    public MavenSourceGenerationConsole(@Nonnull Project project) {
        super(project);
    }

    @Override
    @Nonnull
    protected String getTitle() {
        return RunnerBundle.message("maven.generate.sources.title");
    }

    @Override
    @Nonnull
    protected String getMessage() {
        return "";
    }

    public void startSourceGeneration(@Nonnull String folder) {
        startTask(RunnerBundle.message("maven.generate.sources.task", folder));
    }

    public void finishSourceGeneration(@Nonnull String folder) {
        BuildEventFactory eventFactory = Application.get().getInstance(BuildEventFactory.class);

        completeTask(RunnerBundle.message("maven.generate.sources.task", folder), eventFactory.createSuccessResult());
    }
}
