// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.BuildIssueEventImpl;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.buildtool.quickfix.ChooseAnotherJdkQuickFix;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.execution.RunnerBundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class Maven4UpdateJdkTo17 implements MavenLoggedEventParser {
    private static final String PREFIX = "Error: Apache Maven 4.x requires Java 17 or newer to run";

    @Override
    public boolean supportsType(@Nullable LogMessageType type) {
        return type == null;
    }

    @Override
    public boolean checkLogLine(@Nonnull Object parentId,
                                @Nonnull MavenParsingContext parsingContext,
                                @Nonnull MavenLogEntryReader.MavenLogEntry logLine,
                                @Nonnull MavenLogEntryReader logEntryReader,
                                @Nonnull Consumer<? super BuildEvent> messageConsumer) {
        String line = logLine.getLine();
        if (line.startsWith(PREFIX)) {
            List<String> lines = new ArrayList<>();
            lines.add(line);

            List<MavenLogEntryReader.MavenLogEntry> additionalLines = logEntryReader.readWhile(entry -> entry.getType() == null);
            for (MavenLogEntryReader.MavenLogEntry entry : additionalLines) {
                lines.add(entry.getLine());
            }

            String concatenated = String.join("<br/>", lines);
            BuildEvent event = new BuildIssueEventImpl(
                parentId,
                new WrongRunnerJdkVersion(concatenated, parsingContext.getRunConfiguration()),
                MessageEvent.Kind.ERROR
            );
            messageConsumer.accept(event);
            return true;
        }
        return false;
    }
}

class WrongRunnerJdkVersion implements BuildIssue {
    private final String mvnMessage;
    private final MavenRunConfiguration runConfiguration;

    WrongRunnerJdkVersion(@Nonnull String mvnMessage, @Nonnull MavenRunConfiguration runConfiguration) {
        this.mvnMessage = mvnMessage;
        this.runConfiguration = runConfiguration;
    }

    @Nonnull
    @Override
    public String getTitle() {
        return RunnerBundle.message("maven.4.old.jdk");
    }

    @Nonnull
    @Override
    public String getDescription() {
        return mvnMessage + "\n<br/>" + RunnerBundle.message("maven.4.old.jdk.modify.config.quick.fix",
            ChooseAnotherJdkQuickFix.ID, OpenRunConfigurationQuickFix.ID);
    }

    @Nonnull
    @Override
    public List<BuildIssueQuickFix> getQuickFixes() {
        return Collections.singletonList(new ChooseAnotherJdkQuickFix());
    }

    @Nullable
    @Override
    public Navigatable getNavigatable(@Nonnull Project project) {
        return null;
    }
}
