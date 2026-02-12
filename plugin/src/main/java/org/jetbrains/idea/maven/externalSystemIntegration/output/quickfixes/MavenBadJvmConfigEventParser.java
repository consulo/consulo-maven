// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import consulo.build.ui.event.BuildEvent;
import consulo.build.ui.event.MessageEvent;
import consulo.build.ui.impl.internal.event.BuildIssueEventImpl;
import consulo.build.ui.issue.BuildIssue;
import consulo.build.ui.issue.BuildIssueQuickFix;
import consulo.build.ui.output.BuildOutputInstantReader;
import consulo.build.ui.quickFix.OpenFileQuickFix;
import consulo.execution.impl.internal.configuration.RunManagerImpl;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.ui.ex.action.DataContext;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenImportingSettingsQuickFix;
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenRunnerSettingsQuickFix;
import org.jetbrains.idea.maven.execution.MavenExternalParameters;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.MavenImportLoggedEventParser;
import org.jetbrains.idea.maven.project.MavenConfigurableBundle;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MavenBadJvmConfigEventParser implements MavenLoggedEventParser {
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
        String errorLine = logLine.getLine();
        if (errorLine.startsWith(MavenJvmConfigBuildIssue.VM_INIT_ERROR)) {
            MavenLogEntryReader.MavenLogEntry causeLine = logEntryReader.readLine();
            String causeText = causeLine != null ? causeLine.getLine() : "";
            if (!causeText.isEmpty()) {
                errorLine += "\n" + causeText;
            }
            messageConsumer.accept(
                new BuildIssueEventImpl(
                    parentId,
                    MavenJvmConfigBuildIssue.getRunnerIssue(logLine.getLine(), errorLine, parsingContext.getIdeaProject(), parsingContext.getRunConfiguration()),
                    MessageEvent.Kind.ERROR
                )
            );
            return true;
        }

        return false;
    }
}

class MavenImportBadJvmConfigEventParser implements MavenImportLoggedEventParser {

    @Override
    public boolean processLogLine(@Nonnull Project project,
                                  @Nonnull String logLine,
                                  @Nullable BuildOutputInstantReader reader,
                                  @Nonnull Consumer<? super BuildEvent> messageConsumer) {
        String errorLine = logLine;
        if (logLine.startsWith(MavenJvmConfigBuildIssue.VM_INIT_ERROR)) {
            String causeLine = reader != null ? reader.readLine() : "";
            if (causeLine != null && !causeLine.isEmpty()) {
                errorLine += "\n" + causeLine;
            }
            messageConsumer.accept(new BuildIssueEventImpl(new Object(),
                MavenJvmConfigBuildIssue.getImportIssue(logLine, errorLine, project),
                MessageEvent.Kind.ERROR));
            return true;
        }

        return false;
    }
}

class MavenJvmConfigOpenQuickFix implements BuildIssueQuickFix {
    private final VirtualFile jvmConfig;

    MavenJvmConfigOpenQuickFix(@Nonnull VirtualFile jvmConfig) {
        this.jvmConfig = jvmConfig;
    }

    @Nonnull
    @Override
    public String getId() {
        return "open_maven_jvm_config_quick_fix_" + jvmConfig;
    }

    @Nonnull
    @Override
    public CompletableFuture<?> runQuickFix(@Nonnull Project project, @Nonnull DataContext dataContext) {
        OpenFileQuickFix.showFile(project, jvmConfig.toNioPath(), null);
        return CompletableFuture.completedFuture(null);
    }
}

final class MavenJvmConfigBuildIssue {
    static final String VM_INIT_ERROR = "Error occurred during initialization of VM";

    private MavenJvmConfigBuildIssue() {
    }

    @Nonnull
    static BuildIssue getRunnerIssue(@Nonnull String title,
                                     @Nonnull String errorMessage,
                                     @Nonnull Project project,
                                     @Nonnull MavenRunConfiguration runConfiguration) {
        VirtualFile jvmConfig = MavenExternalParameters.getJvmConfig(runConfiguration.getRunnerParameters().getWorkingDirPath());

        MavenProject mavenProject = null;
        Path workingDirPath = Paths.get(runConfiguration.getRunnerParameters().getWorkingDirPath());
        for (VirtualFile projectFile : MavenProjectsManager.getInstance(project).getProjectsFiles()) {
            if (workingDirPath.equals(projectFile.toNioPath().getParent())) {
                mavenProject = MavenProjectsManager.getInstance(project).findProject(projectFile);
                break;
            }
        }

        List<BuildIssueQuickFix> quickFixes = new ArrayList<>();
        StringBuilder issueDescription = new StringBuilder(errorMessage);
        issueDescription.append("\n\n");
        issueDescription.append(MavenProjectBundle.message("maven.quickfix.header.possible.solution"));
        issueDescription.append("\n");

        OpenMavenRunnerSettingsQuickFix openMavenRunnerSettingsQuickFix =
            new OpenMavenRunnerSettingsQuickFix(MavenConfigurableBundle.message("maven.settings.runner.vm.options"));
        quickFixes.add(openMavenRunnerSettingsQuickFix);
        issueDescription.append(MavenProjectBundle.message("maven.quickfix.jvm.options.runner.settings", openMavenRunnerSettingsQuickFix.getId()));
        issueDescription.append("\n");

        var configurationById = RunManagerImpl.getInstanceImpl(project)
            .findConfigurationByTypeAndName(MavenRunConfigurationType.getInstance(), runConfiguration.getName());
        if (configurationById != null && configurationById.getConfiguration() instanceof MavenRunConfiguration config) {
            var runnerSettings = config.getRunnerSettings();
            if (runnerSettings != null && runnerSettings.getVmOptions() != null && !runnerSettings.getVmOptions().isBlank()) {
                MavenRunConfigurationOpenQuickFix mavenRunConfigurationOpenQuickFix = new MavenRunConfigurationOpenQuickFix(configurationById);
                quickFixes.add(mavenRunConfigurationOpenQuickFix);
                issueDescription.append(MavenProjectBundle
                    .message("maven.quickfix.jvm.options.run.configuration", mavenRunConfigurationOpenQuickFix.getId()));
                issueDescription.append("\n");
            }
        }

        if (jvmConfig != null) {
            MavenJvmConfigOpenQuickFix mavenJvmConfigOpenQuickFix = new MavenJvmConfigOpenQuickFix(jvmConfig);
            quickFixes.add(mavenJvmConfigOpenQuickFix);
            issueDescription.append(MavenProjectBundle.message("maven.quickfix.jvm.options.config.file",
                mavenProject != null ? mavenProject.getDisplayName() : "",
                mavenJvmConfigOpenQuickFix.getId()));
        }

        String finalDescription = issueDescription.toString();
        return new BuildIssue() {
            @Nonnull
            @Override
            public String getTitle() {
                return title;
            }

            @Nonnull
            @Override
            public String getDescription() {
                return finalDescription;
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

    @Nonnull
    static BuildIssue getImportIssue(@Nonnull String title, @Nonnull String errorMessage, @Nonnull Project project) {
        List<BuildIssueQuickFix> quickFixes = new ArrayList<>();
        StringBuilder issueDescription = new StringBuilder(errorMessage);
        issueDescription.append("\n\n");
        issueDescription.append(MavenProjectBundle.message("maven.quickfix.header.possible.solution"));
        issueDescription.append("\n");

        OpenMavenImportingSettingsQuickFix openMavenImportingSettingsQuickFix =
            new OpenMavenImportingSettingsQuickFix(MavenConfigurableBundle.message("maven.settings.importing.vm.options"));
        quickFixes.add(openMavenImportingSettingsQuickFix);
        issueDescription.append(
            MavenProjectBundle.message("maven.quickfix.jvm.options.import.settings", openMavenImportingSettingsQuickFix.getId()));

        for (MavenProject rootProject : MavenProjectsManager.getInstance(project).getRootProjects()) {
            String multimoduleDir = MavenDistributionsCache.getInstance(project).getMultimoduleDirectory(rootProject.getFile().getPath());
            VirtualFile jvmConfig = MavenExternalParameters.getJvmConfig(multimoduleDir);
            if (jvmConfig != null) {
                MavenJvmConfigOpenQuickFix mavenJvmConfigOpenQuickFix = new MavenJvmConfigOpenQuickFix(jvmConfig);
                quickFixes.add(mavenJvmConfigOpenQuickFix);
                issueDescription.append("\n");
                issueDescription.append(MavenProjectBundle
                    .message("maven.quickfix.jvm.options.config.file", rootProject.getDisplayName(), mavenJvmConfigOpenQuickFix.getId()));
            }
        }

        String finalDescription = issueDescription.toString();
        return new BuildIssue() {
            @Nonnull
            @Override
            public String getTitle() {
                return title;
            }

            @Nonnull
            @Override
            public String getDescription() {
                return finalDescription;
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
