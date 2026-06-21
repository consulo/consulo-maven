// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import consulo.build.ui.event.BuildEvent;
import consulo.build.ui.event.BuildEventFactory;
import consulo.build.ui.event.FailureResult;
import consulo.build.ui.output.BuildOutputInstantReader;
import consulo.build.ui.output.BuildOutputParser;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.*;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class MavenLogOutputParser implements BuildOutputParser {
    private final List<MavenLoggedEventParser> myRegisteredEvents;
    private final ExternalSystemTaskId myTaskId;

    private final MavenSpyOutputParser mavenSpyOutputParser;
    private final MavenParsingContext myParsingContext;

    private final BuildEventFactory myBuildEventFactory;

    public MavenLogOutputParser(
        @Nonnull BuildEventFactory buildEventFactory,
        @Nonnull MavenRunConfiguration runConfiguration,
        @Nonnull ExternalSystemTaskId taskId,
        @Nonnull List<MavenLoggedEventParser> registeredEvents,
        boolean useWrapperedLogging
    ) {
        this(buildEventFactory, runConfiguration, taskId, Function.identity(), registeredEvents, useWrapperedLogging);
    }

    public MavenLogOutputParser(
        @Nonnull BuildEventFactory buildEventFactory,
        @Nonnull MavenRunConfiguration runConfiguration,
        @Nonnull ExternalSystemTaskId taskId,
        @Nonnull Function<String, String> targetFileMapper,
        @Nonnull List<MavenLoggedEventParser> registeredEvents,
        boolean useWrapperedLogging
    ) {
        myBuildEventFactory = buildEventFactory;
        myRegisteredEvents = registeredEvents;
        myTaskId = taskId;
        myParsingContext = new MavenParsingContext(runConfiguration, taskId, targetFileMapper);
        SpyOutputExtractor extractor = useWrapperedLogging ? new Maven4SpyOutputExtractor() : new Maven3SpyOutputExtractor();
        mavenSpyOutputParser = new MavenSpyOutputParser(myParsingContext, extractor);
    }

    public synchronized void finish(Consumer<? super BuildEvent> messageConsumer) {
        completeParsers(messageConsumer);

        if (!myParsingContext.isSessionEnded()) {
            messageConsumer.accept(myBuildEventFactory.createFinishBuildEvent(
                myTaskId,
                null,
                System.currentTimeMillis(),
                LocalizeValue.empty(),
                new MavenTaskFailedResultImpl(null, myBuildEventFactory)
            ));
        }
    }

    public MavenParsingContext getParsingContext() {
        return myParsingContext;
    }

    private void completeParsers(Consumer<? super BuildEvent> messageConsumer) {
        for (MavenLoggedEventParser parser : myRegisteredEvents) {
            parser.finish(myTaskId, messageConsumer);
        }
    }

    @Override
    public boolean parse(String line, BuildOutputInstantReader reader, Consumer<? super BuildEvent> messageConsumer) {
        if (myParsingContext.isSessionEnded()) {
            checkErrorAfterMavenSessionEnded(line, messageConsumer);
            return false;
        }

        if (StringUtil.isEmptyOrSpaces(line)) {
            return false;
        }
        if (mavenSpyOutputParser.isSpyLog(line)) {
            mavenSpyOutputParser.processLine(line, messageConsumer);
            if (myParsingContext.isSessionEnded()) {
                completeParsers(messageConsumer);
            }
            return true;
        }
        else {
            sendMessageToAllParents(line, messageConsumer);
            MavenLogEntryReader.MavenLogEntry logLine = nextLine(line);

            MavenLogEntryReader mavenLogReader = wrapReader(reader);

            for (MavenLoggedEventParser event : myRegisteredEvents) {
                if (!event.supportsType(logLine.type())) {
                    continue;
                }
                if (event.checkLogLine(myParsingContext.getLastId(), myParsingContext, logLine, mavenLogReader, messageConsumer)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkErrorAfterMavenSessionEnded(String line, Consumer<? super BuildEvent> messageConsumer) {
        if (!myParsingContext.isProjectFailure() && line.contains("[ERROR]")) {
            myParsingContext.setProjectFailure(true);
                messageConsumer.accept(myBuildEventFactory.createFinishBuildEvent(
                    myTaskId,
                    null,
                    System.currentTimeMillis(),
                    LocalizeValue.empty(),
                    myBuildEventFactory.newFailure().createResult()
                ));
        }
    }

    private void sendMessageToAllParents(String line, Consumer<? super BuildEvent> messageConsumer) {
        List<MavenParsingContext.MavenExecutionEntry> ids = myParsingContext.getAllEntriesReversed();
        for (MavenParsingContext.MavenExecutionEntry entry : ids) {
            if (entry.getId() == myTaskId) {
                return;
            }
            messageConsumer.accept(myBuildEventFactory.createOutputBuildEvent(entry.getId(), withSeparator(line), true));
        }
    }

    private static String withSeparator(@Nonnull String line) {
        if (line.endsWith("\n")) {
            return line;
        }
        return line + "\n";
    }

    private static MavenLogEntryReader wrapReader(BuildOutputInstantReader reader) {
        return new MavenLogEntryReader() {
            @Override
            public void pushBack() {
                reader.pushBack();
            }

            @Override
            public @Nullable MavenLogEntry readLine() {
                return nextLine(reader.readLine());
            }
        };
    }

    private static @Nullable MavenLogEntryReader.MavenLogEntry nextLine(String line) {
        if (line == null) {
            return null;
        }
        return new MavenLogEntryReader.MavenLogEntry(line);
    }
}
