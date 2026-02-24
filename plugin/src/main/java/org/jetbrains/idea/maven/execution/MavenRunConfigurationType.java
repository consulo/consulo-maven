/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.execution;

import com.intellij.java.execution.impl.DefaultJavaProgramRunner;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.compiler.execution.CompileStepBeforeRun;
import consulo.compiler.execution.CompileStepBeforeRunNoErrorCheck;
import consulo.execution.BeforeRunTask;
import consulo.execution.RunManager;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.ConfigurationTypeUtil;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.localize.LocalizeValue;
import consulo.maven.icon.MavenIconGroup;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.build.DelegateBuildRunner;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
@ExtensionImpl
public class MavenRunConfigurationType implements ConfigurationType {
    private static final Key<Boolean> IS_DELEGATE_BUILD = Key.create("IS_DELEGATE_BUILD");

    private final ConfigurationFactory myFactory;
    private static final int MAX_NAME_LENGTH = 40;

    public static MavenRunConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(MavenRunConfigurationType.class);
    }

    public MavenRunConfigurationType() {
        myFactory = new ConfigurationFactory(this) {
            @Nonnull
            @Override
            public String getId() {
                // return not localized string - do not break compability
                return "Maven";
            }

            @Override
            public RunConfiguration createTemplateConfiguration(Project project) {
                return new MavenRunConfiguration(project, this, "");
            }

            @Override
            public RunConfiguration createTemplateConfiguration(Project project, RunManager runManager) {
                return new MavenRunConfiguration(project, this, "");
            }

            @Override
            public RunConfiguration createConfiguration(String name, RunConfiguration template) {
                MavenRunConfiguration cfg = (MavenRunConfiguration)super.createConfiguration(name, template);

                if (!StringUtil.isEmptyOrSpaces(cfg.getRunnerParameters().getWorkingDirPath())) {
                    return cfg;
                }

                Project project = cfg.getProject();
                if (project == null) {
                    return cfg;
                }

                MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);

                List<MavenProject> projects = projectsManager.getProjects();
                if (projects.size() != 1) {
                    return cfg;
                }

                VirtualFile directory = projects.get(0).getDirectoryFile();

                cfg.getRunnerParameters().setWorkingDirPath(directory.getPath());

                return cfg;
            }

            @Override
            public void configureBeforeRunTaskDefaults(Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
                if (providerID == CompileStepBeforeRun.ID || providerID == CompileStepBeforeRunNoErrorCheck.ID) {
                    task.setEnabled(false);
                }
            }
        };
    }

    public static boolean isDelegate(ExecutionEnvironment environment) {
        Boolean res = IS_DELEGATE_BUILD.get(environment);
        return res != null && res;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return MavenRunnerLocalize.mavenRunConfigurationName();
    }

    @Nonnull
    @Override
    public LocalizeValue getConfigurationTypeDescription() {
        return MavenRunnerLocalize.mavenRunConfigurationDescription();
    }

    @Override
    public Image getIcon() {
        return MavenIconGroup.mavenlogo();
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[]{myFactory};
    }

    @Nonnull
    @Override
    public String getId() {
        return "MavenRunConfiguration";
    }

    public static String generateName(Project project, MavenRunnerParameters runnerParameters) {
        StringBuilder stringBuilder = new StringBuilder();

        final String name = getMavenProjectName(project, runnerParameters);
        if (!StringUtil.isEmptyOrSpaces(name)) {
            stringBuilder.append(name);
            stringBuilder.append(" ");
        }

        stringBuilder.append("[");
        listGoals(stringBuilder, runnerParameters.getGoals());
        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    private static void listGoals(final StringBuilder stringBuilder, final List<String> goals) {
        int index = 0;
        for (String goal : goals) {
            if (index != 0) {
                if (stringBuilder.length() + goal.length() < MAX_NAME_LENGTH) {
                    stringBuilder.append(",");
                }
                else {
                    stringBuilder.append("...");
                    break;
                }
            }
            stringBuilder.append(goal);
            index++;
        }
    }

    @Nullable
    private static String getMavenProjectName(final Project project, final MavenRunnerParameters runnerParameters) {
        final VirtualFile virtualFile =
            LocalFileSystem.getInstance().refreshAndFindFileByPath(runnerParameters.getWorkingDirPath() + "/pom.xml");
        if (virtualFile != null) {
            MavenProject mavenProject = MavenProjectsManager.getInstance(project).findProject(virtualFile);
            if (mavenProject != null) {
                if (!StringUtil.isEmptyOrSpaces(mavenProject.getMavenId().getArtifactId())) {
                    return mavenProject.getMavenId().getArtifactId();
                }
            }
        }
        return null;
    }


    public static void runConfiguration(
        Project project,
        MavenRunnerParameters params,
        @Nullable ProgramRunner.Callback callback
    ) {
        runConfiguration(project, params, null, null, callback);
    }

    public static void runConfiguration(
        Project project,
        @Nonnull MavenRunnerParameters params,
        @Nullable MavenGeneralSettings settings,
        @Nullable MavenRunnerSettings runnerSettings,
        @Nullable ProgramRunner.Callback callback
    ) {
        RunnerAndConfigurationSettings configSettings = createRunnerAndConfigurationSettings(
            settings,
            runnerSettings,
            params,
            project
        );

        ProgramRunner runner = DefaultJavaProgramRunner.getInstance();
        Executor executor = DefaultRunExecutor.getRunExecutorInstance();
        ExecutionEnvironment env = new ExecutionEnvironment(executor, runner, configSettings, project);

        try {
            runner.execute(env, callback);
        }
        catch (ExecutionException e) {
            MavenUtil.showError(project, "Failed to execute Maven goal", e);
        }
    }

    public static RunnerAndConfigurationSettings createRunnerAndConfigurationSettings(
        @Nullable MavenGeneralSettings generalSettings,
        @Nullable MavenRunnerSettings runnerSettings,
        MavenRunnerParameters params,
        Project project
    ) {
        MavenRunConfigurationType type = ConfigurationTypeUtil.findConfigurationType(MavenRunConfigurationType.class);

        final RunnerAndConfigurationSettings settings =
            RunManager.getInstance(project).createRunConfiguration(generateName(project, params), type.myFactory);
        MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
        runConfiguration.setRunnerParameters(params);
        runConfiguration.setGeneralSettings(generalSettings);
        runConfiguration.setRunnerSettings(runnerSettings);

        return settings;
    }

    public static @NotNull RunnerAndConfigurationSettings createRunnerAndConfigurationSettings(@Nullable MavenGeneralSettings generalSettings,
                                                                                               @Nullable MavenRunnerSettings runnerSettings,
                                                                                               @NotNull MavenRunnerParameters params,
                                                                                               @NotNull Project project,
                                                                                               @NotNull String name,
                                                                                               boolean isDelegate) {
        MavenRunConfigurationType type = ConfigurationTypeUtil.findConfigurationType(MavenRunConfigurationType.class);

        RunnerAndConfigurationSettings settings = RunManager.getInstance(project).createRunConfiguration(name, type.myFactory);
        MavenRunConfiguration runConfiguration = (MavenRunConfiguration) settings.getConfiguration();
        if (isDelegate) {
            RunManager.getInstance(project).setBeforeRunTasks(runConfiguration, List.of(), false);
        }
        MavenGeneralSettings generalSettingsToRun =
            generalSettings != null ? generalSettings : MavenWorkspaceSettingsComponent.getInstance(project).getSettings().generalSettings;
        MavenRunnerSettings runnerSettingsToRun = runnerSettings != null ? runnerSettings : MavenRunner.getInstance(project).getState();
        runConfiguration.setRunnerParameters(params);
        runConfiguration.setGeneralSettings(generalSettingsToRun);
        runConfiguration.setRunnerSettings(runnerSettingsToRun);
        return settings;
    }

    public static void runConfiguration(Project project,
                                        @NotNull MavenRunnerParameters params,
                                        @Nullable MavenGeneralSettings settings,
                                        @Nullable MavenRunnerSettings runnerSettings,
                                        @Nullable ProgramRunner.Callback callback,
                                        boolean isDelegateBuild) {

        RunnerAndConfigurationSettings configSettings = createRunnerAndConfigurationSettings(settings,
            runnerSettings,
            params,
            project,
            generateName(project, params),
            isDelegateBuild
        );

        ProgramRunner runner = isDelegateBuild ? DelegateBuildRunner.Util.getDelegateRunner() : DefaultJavaProgramRunner.getInstance();
        Executor executor = DefaultRunExecutor.getRunExecutorInstance();
        ExecutionEnvironment environment = new ExecutionEnvironment(executor, runner, configSettings, project);
        environment.putUserData(IS_DELEGATE_BUILD, isDelegateBuild);
        environment.setCallback(callback);
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                runner.execute(environment);
            }
            catch (ExecutionException e) {
                MavenUtil.showError(project, RunnerBundle.message("notification.title.failed.to.execute.maven.goal"), e);
            }
        });
    }
}
