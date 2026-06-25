// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import consulo.application.Application;
import consulo.build.ui.event.BuildEvent;
import consulo.build.ui.event.BuildEventFactory;
import consulo.build.ui.event.MessageEvent;
import consulo.localize.LocalizeValue;
import consulo.project.ui.notification.NotificationGroup;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public abstract class MessageNotifier implements MavenLoggedEventParser {
    private final @Nonnull LogMessageType myType;
    private final @Nonnull MessageEvent.Kind myKind;
    private final @Nonnull NotificationGroup myGroup;
    private final Set<LocalizeValue> myMessages = new HashSet<>();

    protected MessageNotifier(@Nonnull LogMessageType type, @Nonnull MessageEvent.Kind kind, @Nonnull NotificationGroup group) {
        myType = type;
        myKind = kind;
        myGroup = group;
    }

    @Override
    public boolean supportsType(@Nullable LogMessageType type) {
        return type == myType;
    }

    @Override
    public boolean checkLogLine(@Nonnull Object parendId,
                                @Nonnull MavenParsingContext parsingContext,
                                @Nonnull MavenLogEntryReader.MavenLogEntry logLine,
                                @Nonnull MavenLogEntryReader logEntryReader,
                                @Nonnull Consumer<? super BuildEvent> messageConsumer) {
        String line = logLine.line();

        List<MavenLogEntryReader.MavenLogEntry> toConcat = logEntryReader.readWhile(l -> l.type() == myType);
        LocalizeValue concatenated =
            LocalizeValue.of(line + "\n" + StringUtil.join(toConcat, MavenLogEntryReader.MavenLogEntry::line, "\n"));
        LocalizeValue message = getMessage(line, toConcat);
        if (message.isNotEmpty() && myMessages.add(message)) {
            messageConsumer.accept(
                Application.get().getInstance(BuildEventFactory.class).createMessageEvent(parendId, myKind, myGroup, message, concatenated)
            );
            return true;
        }
        return false;
    }

    protected LocalizeValue getMessage(String line, List<MavenLogEntryReader.MavenLogEntry> toConcat) {
        if (toConcat == null || toConcat.isEmpty()) {
            return LocalizeValue.of(line);
        }
        if (!StringUtil.isEmptyOrSpaces(line)) {
            return LocalizeValue.of(line);
        }
        MavenLogEntryReader.MavenLogEntry entry = ContainerUtil.find(toConcat, e -> !StringUtil.isEmptyOrSpaces(e.line()));

        if (entry != null) {
            return LocalizeValue.of(entry.line());
        }

        return LocalizeValue.empty();
    }
}
