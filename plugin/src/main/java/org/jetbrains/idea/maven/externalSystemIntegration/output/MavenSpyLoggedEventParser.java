// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.build.ui.event.BuildEvent;
import consulo.component.extension.ExtensionPointName;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenEventType;

import java.util.function.Consumer;

/**
 * Log line parser for maven spy log - task execution process.
 * {@link MavenSpyOutputParser)
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MavenSpyLoggedEventParser {
    ExtensionPointName<MavenSpyLoggedEventParser> EP_NAME = ExtensionPointName.create(MavenSpyLoggedEventParser.class);

    boolean supportsType(@Nonnull MavenEventType type);

    /**
     * Process log line.
     *
     * @param parentId        - node id from BuildTreeConsoleView.
     * @param parsingContext  - maven parsing context.
     * @param logLine         - log line text.
     * @param messageConsumer build event consumer.
     * @return true if log line consumed.
     */
    boolean processLogLine(
        @Nonnull Object parentId,
        @Nonnull MavenParsingContext parsingContext,
        @Nonnull String logLine,
        @Nonnull Consumer<? super BuildEvent> messageConsumer);
}
