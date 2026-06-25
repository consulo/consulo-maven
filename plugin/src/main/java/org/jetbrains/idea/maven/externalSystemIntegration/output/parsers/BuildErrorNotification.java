// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import consulo.build.ui.FilePosition;
import consulo.build.ui.event.BuildEvent;
import consulo.build.ui.event.BuildEventFactory;
import consulo.build.ui.event.MessageEvent;
import consulo.localize.LocalizeValue;
import consulo.project.ui.notification.NotificationGroup;
import consulo.util.io.FileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;

import java.io.File;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BuildErrorNotification implements MavenLoggedEventParser {
    private static final Pattern LINE_AND_COLUMN = Pattern.compile("[^\\d]+?(\\d+)[^\\d]+(\\d+)");
    private static final Pattern LINE_ONLY = Pattern.compile("[^\\d]+?(\\d+)");
    private static final String MESSAGE_SKIP_CHARS = " \t:])";

    private final String myLanguage;
    private final String myExtension;
    private final NotificationGroup myMessageGroup;
    private final BuildEventFactory myBuildEventFactory;

    protected BuildErrorNotification(
        String language,
        String extension,
        NotificationGroup messageGroup,
        BuildEventFactory buildEventFactory
    ) {
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
    public boolean checkLogLine(
        @Nonnull Object parentId,
        @Nonnull MavenParsingContext parsingContext,
        @Nonnull MavenLogEntryReader.MavenLogEntry logLine,
        @Nonnull MavenLogEntryReader logEntryReader,
        @Nonnull Consumer<? super BuildEvent> messageConsumer
    ) {
        String line = logLine.line();
        if (line.endsWith("java.lang.OutOfMemoryError")) {
            messageConsumer.accept(myBuildEventFactory.createMessageEvent(
                parentId,
                MessageEvent.Kind.ERROR,
                myMessageGroup,
                MavenRunnerLocalize.buildEventMessageOutMemory(),
                LocalizeValue.of(line)
            ));
            return true;
        }
        int fileNameIdx = line.indexOf("." + myExtension + ":");
        if (fileNameIdx < 0) {
            return false;
        }
        int fullFileNameIdx = line.indexOf(':', fileNameIdx);
        String targetFileNameWithExtension = line.substring(0, fullFileNameIdx);
        File parsedFile = parsingContext.toLocalFile(targetFileNameWithExtension);
        String lineWithPosition = line.substring(fullFileNameIdx);
        Matcher matcher = getMatcher(lineWithPosition);
        String message;
        FilePosition position;

        if (matcher == null) {
            position = new FilePosition(parsedFile);
            message = lineWithPosition;
        }
        else {
            position = withLineAndColumn(parsedFile, matcher);
            message = lineWithPosition.substring(matcher.end());
        }

        LocalizeValue errorMessage = getErrorMessage(message);
        messageConsumer.accept(myBuildEventFactory.createFileMessageEvent(
            parentId,
            MessageEvent.Kind.ERROR,
            myMessageGroup,
            errorMessage,
            errorMessage,
            position
        ));
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
    private static LocalizeValue getErrorMessage(@Nonnull String message) {
        int i = 0, n = message.length();
        while (i < n && MESSAGE_SKIP_CHARS.indexOf(message.charAt(i)) >= 0) {
            i++;
        }
        return LocalizeValue.of(message.substring(i).trim());
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
            return new FilePosition(toTest);
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
