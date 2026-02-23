// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import consulo.application.Application;
import consulo.build.ui.BuildDescriptor;
import consulo.build.ui.event.BuildEventFactory;
import consulo.build.ui.event.StartBuildEvent;
import consulo.build.ui.output.BuildOutputInstantReader;
import consulo.build.ui.output.BuildOutputService;
import consulo.build.ui.progress.BuildProgressListener;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.process.ProcessOutputType;
import consulo.process.util.AnsiEscapeDecoder;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogOutputParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenOutputParserProvider;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Function;

public class MavenBuildEventProcessor implements AnsiEscapeDecoder.ColoredTextAcceptor {
    private final @Nonnull BuildProgressListener myBuildProgressListener;
    private final @Nonnull BuildOutputInstantReader.Primary myInstantReader;
    private final @Nonnull MavenLogOutputParser myParser;
    private boolean closed = false;
    private final BuildDescriptor myDescriptor;
    private final @Nonnull Function<MavenParsingContext, StartBuildEvent> myStartBuildEventSupplier;

    public MavenBuildEventProcessor(@Nonnull MavenRunConfiguration runConfiguration,
                                    @Nonnull BuildProgressListener buildProgressListener,
                                    @Nonnull BuildDescriptor descriptor,
                                    @Nonnull ExternalSystemTaskId taskId,
                                    @Nonnull Function<String, String> targetFileMapper,
                                    @Nullable Function<MavenParsingContext, StartBuildEvent> startBuildEventSupplier,
                                    boolean useWrapperedLogging) {

        myBuildProgressListener = buildProgressListener;
        myDescriptor = descriptor;

        Application application = Application.get();

        BuildEventFactory buildEventFactory = application.getInstance(BuildEventFactory.class);

        myStartBuildEventSupplier = startBuildEventSupplier != null
            ? startBuildEventSupplier : ctx -> buildEventFactory.createStartBuildEvent(myDescriptor, "");

        myParser = MavenOutputParserProvider.createMavenOutputParser(buildEventFactory, runConfiguration, taskId, targetFileMapper, useWrapperedLogging);

        BuildOutputService buildOutputService = application.getInstance(BuildOutputService.class);

        myInstantReader = buildOutputService.createBuildOutputInstantReader(
            taskId,
            taskId,
            myBuildProgressListener,
            Collections.singletonList(myParser)
        );
    }

    public synchronized void finish() {
        myParser.finish(e -> myBuildProgressListener.onEvent(myDescriptor.getId(), e));
        try {
            myInstantReader.close();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        closed = true;
    }

    public void start() {
        StartBuildEvent startEvent = myStartBuildEventSupplier.apply(getParsingContext());

        myBuildProgressListener.onEvent(myDescriptor.getId(), startEvent);
    }

    public synchronized void onTextAvailable(String text, boolean stdError) {
        if (!closed) {
            try {
                myInstantReader.append(text);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public MavenParsingContext getParsingContext() {
        return myParser.getParsingContext();
    }

    @Override
    public void coloredTextAvailable(@Nonnull String text, @Nonnull Key outputType) {
        onTextAvailable(text, ProcessOutputType.isStderr(outputType));
    }
}
