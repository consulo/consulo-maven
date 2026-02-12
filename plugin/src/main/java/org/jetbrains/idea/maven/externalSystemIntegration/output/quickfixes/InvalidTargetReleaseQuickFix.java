// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.BuildIssueEventImpl;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.pom.java.LanguageLevel;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.project.ProjectRootManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenRunnerSettingsQuickFix;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenSpyLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenEventType;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.List;
import java.util.function.Consumer;

public class InvalidTargetReleaseQuickFix implements MavenSpyLoggedEventParser {

    @Override
    public boolean supportsType(@Nonnull MavenEventType type) {
        return type == MavenEventType.MOJO_FAILED;
    }

    @Override
    public boolean processLogLine(@Nonnull Object parentId,
                                  @Nonnull MavenParsingContext parsingContext,
                                  @Nonnull String logLine,
                                  @Nonnull Consumer<? super BuildEvent> messageConsumer) {
        if (logLine.contains("invalid target release:")) {
            List<String> startedProjects = parsingContext.getStartedProjects();
            if (startedProjects.isEmpty()) return false;

            String lastErrorProject = startedProjects.get(startedProjects.size() - 1) + ":";

            String failedProject = null;
            for (String p : parsingContext.getProjectsInReactor()) {
                if (p.startsWith(lastErrorProject)) {
                    failedProject = p;
                    break;
                }
            }
            if (failedProject == null) return false;

            Project project = parsingContext.getIdeaProject();
            var mavenProject = MavenProjectsManager.getInstance(project).findProject(new MavenId(failedProject));
            if (mavenProject == null) return false;
            if (mavenProject.getMavenId().getArtifactId() == null) return false;

            Module module = ModuleManager.getInstance(project).findModuleByName(mavenProject.getMavenId().getArtifactId());
            if (module == null) return false;

            String runnerSdkName = getRunnerSdkName(parsingContext, project);
            LanguageLevel requiredLanguageLevel = getLanguageLevelFromLog(logLine);
            if (requiredLanguageLevel == null) return false;

            RunnerAndConfigurationSettings persistedRunConfiguration = RunManager.getInstance(project)
                .findConfigurationByTypeAndName(parsingContext.getRunConfiguration().getType(),
                    parsingContext.getRunConfiguration().getName());

            BuildIssue buildIssue;
            if (persistedRunConfiguration == null || !(persistedRunConfiguration.getConfiguration() instanceof MavenRunConfiguration)) {
                buildIssue = getBuildIssueForDefaultRunner(module.getName(), runnerSdkName, logLine, requiredLanguageLevel);
            } else {
                buildIssue = getBuildIssueForRunConfiguration(module.getName(), persistedRunConfiguration, runnerSdkName, logLine, requiredLanguageLevel);
            }

            messageConsumer.accept(
                new BuildIssueEventImpl(parentId, buildIssue, MessageEvent.Kind.ERROR)
            );
            return true;
        }

        return false;
    }

    @Nullable
    private String getRunnerSdkName(@Nonnull MavenParsingContext parsingContext, @Nonnull Project project) {
        var runnerSettings = parsingContext.getRunConfiguration().getRunnerSettings();
        String jreName = runnerSettings != null ? runnerSettings.getJreName() : null;

        if (jreName == null) {
            return ProjectRootManager.getInstance(project).getProjectSdkName();
        }
        var sdk = ExternalSystemJdkUtil.resolveJdkName(ProjectRootManager.getInstance(project).getProjectSdk(), jreName);
        return sdk != null ? sdk.getName() : null;
    }

    @Nullable
    private LanguageLevel getLanguageLevelFromLog(@Nonnull String logLine) {
        String[] parts = logLine.split(" ");
        String last = parts[parts.length - 1];
        return LanguageLevel.parse(last);
    }

    @Nonnull
    private BuildIssue getBuildIssueForRunConfiguration(@Nonnull String moduleName,
                                                        @Nonnull RunnerAndConfigurationSettings persistedRunConfiguration,
                                                        @Nullable String runnerSdkName,
                                                        @Nonnull String errorMessage,
                                                        @Nonnull LanguageLevel requiredLanguageLevel) {
        MavenRunConfigurationOpenQuickFix setupRunConfigQuickFix = new MavenRunConfigurationOpenQuickFix(persistedRunConfiguration);
        List<BuildIssueQuickFix> quickFixes = List.of(setupRunConfigQuickFix);

        StringBuilder issueDescription = new StringBuilder(errorMessage);
        issueDescription.append("\n\n");
        if (runnerSdkName == null) {
            issueDescription.append(MavenProjectBundle.message("maven.quickfix.invalid.target.release.version.run.config.unknown.sdk",
                moduleName, requiredLanguageLevel.toJavaVersion(), persistedRunConfiguration.getName(), setupRunConfigQuickFix.getId()));
        } else {
            issueDescription.append(MavenProjectBundle.message("maven.quickfix.invalid.target.release.version.run.config",
                runnerSdkName, moduleName, requiredLanguageLevel.toJavaVersion(), persistedRunConfiguration.getName(), setupRunConfigQuickFix.getId()));
        }

        return buildIssue(errorMessage, issueDescription.toString(), quickFixes);
    }

    @Nonnull
    private BuildIssue getBuildIssueForDefaultRunner(@Nonnull String moduleName,
                                                     @Nullable String runnerSdkName,
                                                     @Nonnull String errorMessage,
                                                     @Nonnull LanguageLevel requiredLanguageLevel) {
        OpenMavenRunnerSettingsQuickFix setupRunnerQuickFix = new OpenMavenRunnerSettingsQuickFix("JRE");
        List<BuildIssueQuickFix> quickFixes = List.of(setupRunnerQuickFix);

        StringBuilder issueDescription = new StringBuilder(errorMessage);
        issueDescription.append("\n\n");
        if (runnerSdkName == null) {
            issueDescription.append(MavenProjectBundle.message("maven.quickfix.invalid.target.release.version.unknown.sdk",
                moduleName, requiredLanguageLevel.toJavaVersion(), setupRunnerQuickFix.getId()));
        } else {
            issueDescription.append(MavenProjectBundle.message("maven.quickfix.invalid.target.release.version",
                runnerSdkName, moduleName, requiredLanguageLevel.toJavaVersion(), setupRunnerQuickFix.getId()));
        }

        return buildIssue(errorMessage, issueDescription.toString(), quickFixes);
    }

    @Nonnull
    private BuildIssue buildIssue(@Nonnull String errorMessage,
                                  @Nonnull String issueDescription,
                                  @Nonnull List<BuildIssueQuickFix> quickFixes) {
        return new BuildIssue() {
            @Nonnull
            @Override
            public String getTitle() {
                return errorMessage;
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
