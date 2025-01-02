// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import consulo.build.ui.event.BuildEvent;
import consulo.build.ui.event.BuildEventFactory;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.ide.impl.idea.build.output.BuildOutputInstantReader;
import consulo.ide.impl.idea.build.output.BuildOutputParser;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenSpyOutputParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenTaskFailedResultImpl;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;


public class MavenLogOutputParser implements BuildOutputParser {
    private final BuildEventFactory myBuildEventFactory;

    private final List<MavenLoggedEventParser> myRegisteredEvents;
    private final ExternalSystemTaskId myTaskId;

    private final MavenSpyOutputParser mavenSpyOutputParser;
    private final MavenParsingContext myParsingContext;

    public MavenLogOutputParser(@Nonnull BuildEventFactory buildEventFactory,
                                @Nonnull MavenRunConfiguration runConfiguration,
                                @Nonnull ExternalSystemTaskId taskId,
                                @Nonnull List<MavenLoggedEventParser> registeredEvents) {
        this(buildEventFactory, runConfiguration, taskId, Function.identity(), registeredEvents);
    }

    public MavenLogOutputParser(@Nonnull BuildEventFactory buildEventFactory,
                                @Nonnull MavenRunConfiguration runConfiguration,
                                @Nonnull ExternalSystemTaskId taskId,
                                @Nonnull Function<String, String> targetFileMapper,
                                @Nonnull List<MavenLoggedEventParser> registeredEvents) {
        myRegisteredEvents = registeredEvents;
        myBuildEventFactory = buildEventFactory;
        myTaskId = taskId;
        myParsingContext = new MavenParsingContext(runConfiguration, taskId, targetFileMapper);
        mavenSpyOutputParser = new MavenSpyOutputParser(myParsingContext, buildEventFactory);
    }

    public synchronized void finish(Consumer<? super BuildEvent> messageConsumer) {
        completeParsers(messageConsumer);

        if (!myParsingContext.getSessionEnded()) {
            messageConsumer.accept(myBuildEventFactory.createFinishBuildEvent(myTaskId, null, System.currentTimeMillis(), "", new MavenTaskFailedResultImpl(null, myBuildEventFactory)));
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
        if (myParsingContext.getSessionEnded()) {
            checkErrorAfterMavenSessionEnded(line, messageConsumer);
            return false;
        }

        if (line == null || StringUtil.isEmptyOrSpaces(line)) {
            return false;
        }
        if (MavenSpyOutputParser.isSpyLog(line)) {
            mavenSpyOutputParser.processLine(line, messageConsumer);
            if (myParsingContext.getSessionEnded()) {
                completeParsers(messageConsumer);
            }
            return true;
        }
        else {
            sendMessageToAllParents(line, messageConsumer);
            MavenLogEntryReader.MavenLogEntry logLine = nextLine(line);

            MavenLogEntryReader mavenLogReader = wrapReader(reader);

            for (MavenLoggedEventParser event : myRegisteredEvents) {
                if (!event.supportsType(logLine.myType)) {
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
        if (!myParsingContext.getProjectFailure()) {
            if (line.contains("[ERROR]")) {
                myParsingContext.setProjectFailure(true);
                messageConsumer.accept(myBuildEventFactory.createFinishBuildEvent(myTaskId, null, System.currentTimeMillis(), "", myBuildEventFactory.createFailureResult()));
            }
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

            @Nullable
            @Override
            public MavenLogEntry readLine() {
                return nextLine(reader.readLine());
            }
        };
    }

    @Nullable
    private static MavenLogEntryReader.MavenLogEntry nextLine(String line) {
        if (line == null) {
            return null;
        }
        return new MavenLogEntryReader.MavenLogEntry(line);
    }
}
