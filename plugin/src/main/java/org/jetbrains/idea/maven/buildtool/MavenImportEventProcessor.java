// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import consulo.build.ui.event.BuildEvent;
import consulo.build.ui.event.BuildIssueEvent;
import consulo.build.ui.output.BuildOutputInstantReader;
import consulo.build.ui.output.BuildOutputService;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.process.util.AnsiEscapeDecoder;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.importing.output.MavenImportOutputParser;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.IOException;
import java.util.Collections;

public class MavenImportEventProcessor implements AnsiEscapeDecoder.ColoredTextAcceptor {
    private final @Nonnull BuildOutputInstantReader.Primary myInstantReader;
    private final @Nonnull MavenProjectsManager myProjectsManager;

    public MavenImportEventProcessor(@Nonnull Project project) {
        myProjectsManager = MavenProjectsManager.getInstance(project);

        ExternalSystemTaskId taskId = myProjectsManager.getSyncConsole().getTaskId();
        BuildOutputService buildOutputService = project.getApplication().getInstance(BuildOutputService.class);

        myInstantReader = buildOutputService.createBuildOutputInstantReader(
            taskId, taskId,
            (Object buildId, BuildEvent event) -> {
                if (event instanceof BuildIssueEvent) {
                    myProjectsManager.getSyncConsole().addBuildIssue(((BuildIssueEvent) event).getIssue(), ((BuildIssueEvent) event).getKind());
                }
            },
            Collections.singletonList(new MavenImportOutputParser(project))
        );
    }

    public void finish() {
        try {
            myInstantReader.close();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void coloredTextAvailable(@Nonnull String text, @Nonnull Key outputType) {
        try {
            myInstantReader.append(text);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}