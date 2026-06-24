// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import consulo.application.Application;
import consulo.build.ui.event.BuildEventFactory;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;

/**
 * An instance of this class is not supposed to be reused
 */
public class MavenSourceGenerationConsole extends MavenSyncConsoleBase {
    public MavenSourceGenerationConsole(@Nonnull Project project) {
        super(project);
    }

    @Override
    @Nonnull
    protected LocalizeValue getTitle() {
        return MavenRunnerLocalize.mavenGenerateSourcesTitle();
    }

    @Override
    @Nonnull
    protected LocalizeValue getMessage() {
        return LocalizeValue.empty();
    }

    public void startSourceGeneration(@Nonnull String folder) {
        startTask(MavenRunnerLocalize.mavenGenerateSourcesTask(folder));
    }

    public void finishSourceGeneration(@Nonnull String folder) {
        BuildEventFactory eventFactory = Application.get().getInstance(BuildEventFactory.class);

        completeTask(MavenRunnerLocalize.mavenGenerateSourcesTask(folder), eventFactory.createSuccessResult());
    }
}
