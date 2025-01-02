// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import consulo.build.ui.FilePosition;
import consulo.build.ui.event.BuildEvent;
import consulo.build.ui.event.BuildEventFactory;
import consulo.build.ui.event.MessageEvent;
import consulo.project.ui.notification.NotificationGroup;
import consulo.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.execution.RunnerBundle;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;

import java.io.File;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BuildErrorNotification implements MavenLoggedEventParser {
    private static final Pattern LINE_AND_COLUMN = Pattern.compile("[^\\d]+?(\\d+)[^\\d]+(\\d+)");
    private static final Pattern LINE_ONLY = Pattern.compile("[^\\d]+?(\\d+)");
    private final String myLanguage;
    private final String myExtension;
    private final NotificationGroup myMessageGroup;
    private final BuildEventFactory myBuildEventFactory;

    protected BuildErrorNotification(@NonNls String language, @NonNls String extension, NotificationGroup messageGroup, BuildEventFactory buildEventFactory) {
        myLanguage = language;
        myExtension = extension;
        myMessageGroup = messageGroup;
        myBuildEventFactory = buildEventFactory;
    }

    @Override
    public boolean supportsType(@Nullable LogMessageType type) {
        return type == LogMessageType.ERROR;
    }

    @Override
    public boolean checkLogLine(@Nonnull Object parentId,
                                @Nonnull MavenParsingContext parsingContext,
                                @Nonnull MavenLogEntryReader.MavenLogEntry logLine,
                                @Nonnull MavenLogEntryReader logEntryReader,
                                @Nonnull Consumer<? super BuildEvent> messageConsumer) {

        String line = logLine.getLine();
        if (line.endsWith("java.lang.OutOfMemoryError")) {
            messageConsumer.accept(myBuildEventFactory.createMessageEvent(parentId, MessageEvent.Kind.ERROR, myMessageGroup,
                RunnerBundle.message("build.event.message.out.memory"), line));
            return true;
        }
        int fileNameIdx = line.indexOf("." + myExtension + ":");
        if (fileNameIdx < 0) {
            return false;
        }
        int fullFileNameIdx = line.indexOf(":", fileNameIdx);
        if (fullFileNameIdx < 0) {
            return false;
        }
        String targetFileNameWithoutExtension = line.substring(0, fileNameIdx);
        String localFileNameWithoutExtension = parsingContext.getTargetFileMapper().apply(targetFileNameWithoutExtension);
        String filename = FileUtil.toSystemDependentName(localFileNameWithoutExtension + "." + myExtension);

        File parsedFile = new File(filename);
        String lineWithPosition = line.substring(fullFileNameIdx);
        Matcher matcher = getMatcher(lineWithPosition);
        String message;
        FilePosition position;

        if (matcher == null) {
            position = new FilePosition(parsedFile, 0, 0);
            message = lineWithPosition;
        }
        else {
            position = withLineAndColumn(parsedFile, matcher);
            message = lineWithPosition.substring(matcher.end());
        }

        String errorMessage = getErrorMessage(position, message);
        messageConsumer
            .accept(myBuildEventFactory.createFileMessageEvent(parentId, MessageEvent.Kind.ERROR, myMessageGroup, errorMessage, errorMessage,
                position));
        return true;
    }

    private static Matcher getMatcher(String string) {
        Matcher result = LINE_AND_COLUMN.matcher(string);
        if (result.lookingAt()) {
            return result;
        }
        result = LINE_ONLY.matcher(string);
        if (result.lookingAt()) {
            return result;
        }
        return null;
    }

    @Nonnull
    private static String getErrorMessage(@Nonnull FilePosition position, @Nonnull String message) {
        message = message.trim();
        while (message.startsWith(":") || message.startsWith("]") || message.startsWith(")")) {
            message = message.substring(1);
        }
        message = message.trim();

        return message;
    }

    @Nonnull
    private static FilePosition withLineAndColumn(File toTest, Matcher matcher) {
        if (matcher.groupCount() == 2) {
            return new FilePosition(toTest, atoi(matcher.group(1)) - 1, atoi(matcher.group(2)) - 1);
        }
        else if (matcher.groupCount() == 1) {
            return new FilePosition(toTest, atoi(matcher.group(1)) - 1, 0);
        }
        else {
            return new FilePosition(toTest, 0, 0);
        }
    }

    private static int atoi(String s) {
        try {
            return Integer.parseInt(s);
        }
        catch (NumberFormatException ignore) {
            return 0;
        }
    }
}
