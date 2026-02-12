// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.BuildIssueEventImpl;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.build.issue.quickfix.OpenFileQuickFix;
import com.intellij.build.output.BuildOutputInstantReader;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.ui.ex.action.DataContext;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.MavenImportLoggedEventParser;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenConfigParseException;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MavenBadConfigEventParser implements MavenLoggedEventParser {
    @Override
    public boolean supportsType(@Nullable LogMessageType type) {
        return type == null || type == LogMessageType.ERROR;
    }

    @Override
    public boolean checkLogLine(@Nonnull Object parentId,
                                @Nonnull MavenParsingContext parsingContext,
                                @Nonnull MavenLogEntryReader.MavenLogEntry logLine,
                                @Nonnull MavenLogEntryReader logEntryReader,
                                @Nonnull Consumer<? super BuildEvent> messageConsumer) {
        String line = logLine.getLine();
        if (line.startsWith(MavenConfigBuildIssue.CONFIG_PARSE_ERROR) && logLine.getType() == null) {
            BuildIssue buildIssue = MavenConfigBuildIssue.getIssue(
                line, line.substring(MavenConfigBuildIssue.CONFIG_PARSE_ERROR.length()).trim(), parsingContext.getIdeaProject()
            );
            if (buildIssue == null) return false;
            messageConsumer.accept(
                new BuildIssueEventImpl(parentId, buildIssue, MessageEvent.Kind.ERROR)
            );
            return true;
        }
        if (line.startsWith(MavenConfigBuildIssue.CONFIG_VALUE_ERROR) && logLine.getType() == LogMessageType.ERROR) {
            BuildIssue buildIssue = MavenConfigBuildIssue.getIssue(line, line, parsingContext.getIdeaProject());
            if (buildIssue == null) return false;
            messageConsumer.accept(
                new BuildIssueEventImpl(parentId, buildIssue, MessageEvent.Kind.ERROR)
            );
            return true;
        }

        return false;
    }
}

class MavenImportBadConfigEventParser implements MavenImportLoggedEventParser {

    @Override
    public boolean processLogLine(@Nonnull Project project,
                                  @Nonnull String logLine,
                                  @Nullable BuildOutputInstantReader reader,
                                  @Nonnull Consumer<? super BuildEvent> messageConsumer) {
        if (logLine.startsWith(MavenConfigBuildIssue.CONFIG_PARSE_ERROR)) {
            BuildIssue buildIssue = MavenConfigBuildIssue.getIssue(
                logLine, logLine.substring(MavenConfigBuildIssue.CONFIG_PARSE_ERROR.length()).trim(), project
            );
            if (buildIssue == null) return false;
            messageConsumer.accept(
                new BuildIssueEventImpl(new Object(), buildIssue, MessageEvent.Kind.ERROR)
            );
            return true;
        }
        if (logLine.startsWith(MavenConfigBuildIssue.CONFIG_VALUE_ERROR)) {
            BuildIssue buildIssue = MavenConfigBuildIssue.getIssue(logLine, logLine, project);
            if (buildIssue == null) return false;
            messageConsumer.accept(
                new BuildIssueEventImpl(new Object(), buildIssue, MessageEvent.Kind.ERROR)
            );
            return true;
        }

        return false;
    }
}

class MavenConfigOpenQuickFix implements BuildIssueQuickFix {
    private final VirtualFile mavenConfig;
    private final String errorMessage;

    MavenConfigOpenQuickFix(@Nonnull VirtualFile mavenConfig, @Nonnull String errorMessage) {
        this.mavenConfig = mavenConfig;
        this.errorMessage = errorMessage;
    }

    @Nonnull
    @Override
    public String getId() {
        return "open_maven_config_quick_fix";
    }

    @Nonnull
    @Override
    public CompletableFuture<?> runQuickFix(@Nonnull Project project, @Nonnull DataContext dataContext) {
        String search = null;
        if (errorMessage.contains(":")) {
            search = errorMessage.substring(errorMessage.lastIndexOf(":"))
                .replace(":", "")
                .replace("\"", "")
                .replace("'", "")
                .trim();
        }
        OpenFileQuickFix.showFile(project, mavenConfig.toNioPath(), search);
        return CompletableFuture.completedFuture(null);
    }
}

final class MavenConfigBuildIssue {
    static final String CONFIG_PARSE_ERROR = "Unable to parse maven.config:";
    static final String CONFIG_VALUE_ERROR = "For input string:";

    private MavenConfigBuildIssue() {
    }

    @Nullable
    static BuildIssue getIssue(@Nonnull MavenConfigParseException ex) {
        VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(ex.getDirectory());
        if (dir == null) return null;

        VirtualFile configFile = dir.findFileByRelativePath(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH);
        if (configFile == null) return null;

        String message = ex.getLocalizedMessage() != null ? ex.getLocalizedMessage() :
            (ex.getMessage() != null ? ex.getMessage() : ex.toString());
        return getConfigFile(configFile, message, CONFIG_PARSE_ERROR);
    }

    @Nullable
    static BuildIssue getIssue(@Nonnull String title, @Nonnull String errorMessage, @Nonnull Project project) {
        List<MavenProject> rootProjects = MavenProjectsManager.getInstance(project).getRootProjects();
        if (rootProjects.isEmpty()) {
            MavenLog.LOG.warn("Cannot find appropriate maven project, project = " + project.getName());
            return null;
        }

        MavenProject mavenProject = rootProjects.get(0);
        VirtualFile configFile = MavenUtil.getConfigFile(mavenProject, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH);
        if (configFile == null) return null;

        return getConfigFile(configFile, errorMessage, title);
    }

    @Nonnull
    private static BuildIssue getConfigFile(@Nonnull VirtualFile configFile,
                                            @Nonnull String errorMessage,
                                            @Nonnull String title) {
        MavenConfigOpenQuickFix mavenConfigOpenQuickFix = new MavenConfigOpenQuickFix(configFile, errorMessage);
        List<BuildIssueQuickFix> quickFixes = Collections.singletonList(mavenConfigOpenQuickFix);

        String issueDescription = errorMessage + "\n\n" +
            MavenProjectBundle.message("maven.quickfix.maven.config.file", mavenConfigOpenQuickFix.getId());

        return new BuildIssue() {
            @Nonnull
            @Override
            public String getTitle() {
                return title;
            }

            @Nonnull
            @Override
            public String getDescription() {
                return issueDescription;
            }

            @Nonnull
            @Override
            public List<BuildIssueQuickFix> getQuickFixes() {
                return quickFixes;
            }

            @Nullable
            @Override
            public Navigatable getNavigatable(@Nonnull Project project) {
                return null;
            }
        };
    }
}
