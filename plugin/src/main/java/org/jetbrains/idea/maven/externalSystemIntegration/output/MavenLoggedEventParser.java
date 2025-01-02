// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.build.ui.event.BuildEvent;
import consulo.component.extension.ExtensionPointName;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Consumer;

/**
 * Log line parser for maven task execution process (maven build log).
 * Example of use:
 * override fun checkLogLine(...): Boolean {
 * if (logLine.line.contains("error1") && logEntryReader.readLine().line.contains("error2")) {
 * messageConsumer.accept(BuildIssueEventImpl(parentId, BuildIssue(...), MessageEvent.Kind.ERROR));
 * return true
 * }
 * return false
 * }
 * <p>
 * {@link MavenLogOutputParser)
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MavenLoggedEventParser {
    ExtensionPointName<MavenLoggedEventParser> EP_NAME = ExtensionPointName.create(MavenLoggedEventParser.class);

    boolean supportsType(@Nullable LogMessageType type);

    /**
     * Process log line.
     *
     * @param parentId        - node id from BuildTreeConsoleView.
     * @param parsingContext  - maven parsing context.
     * @param logLine         - log line text.
     * @param messageConsumer build event consumer.
     * @return true if log line consumed.
     */
    boolean checkLogLine(@Nonnull Object parentId,
                         @Nonnull MavenParsingContext parsingContext,
                         @Nonnull MavenLogEntryReader.MavenLogEntry logLine,
                         @Nonnull MavenLogEntryReader logEntryReader,
                         @Nonnull Consumer<? super BuildEvent> messageConsumer);


    /**
     * Callback when maven task execution process is finished.
     */
    default void finish(@Nonnull ExternalSystemTaskId taskId, @Nonnull Consumer<? super BuildEvent> messageConsumer) {
    }
}
