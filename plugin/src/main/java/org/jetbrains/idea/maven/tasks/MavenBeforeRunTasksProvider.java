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
package org.jetbrains.idea.maven.tasks;

import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.document.FileDocumentManager;
import consulo.execution.BeforeRunTaskProvider;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.localize.LocalizeValue;
import consulo.maven.icon.MavenIconGroup;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.ParametersListUtil;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.util.concurrent.AsyncResult;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.jetbrains.idea.maven.execution.MavenEditGoalDialog;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.build.DelegateBuildRunner;
import org.jetbrains.idea.maven.localize.MavenTasksLocalize;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.List;

@ExtensionImpl
public class MavenBeforeRunTasksProvider extends BeforeRunTaskProvider<MavenBeforeRunTask> {
    public static final Key<MavenBeforeRunTask> ID = Key.create("Maven.BeforeRunTask");
    private final Project myProject;

    @Inject
    public MavenBeforeRunTasksProvider(Project project) {
        myProject = project;
    }

    @Nonnull
    @Override
    public Key<MavenBeforeRunTask> getId() {
        return ID;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return MavenTasksLocalize.mavenTasksBeforeRunEmpty();
    }

    @Override
    public Image getIcon(@Nonnull RunConfiguration runConfiguration) {
        return MavenIconGroup.mavenlogo();
    }

    @Nonnull
    @Override
    public LocalizeValue getDescription(MavenBeforeRunTask task) {
        MavenProject mavenProject = getMavenProject(task);
        if (mavenProject == null) {
            return MavenTasksLocalize.mavenTasksBeforeRunEmpty();
        }

        String desc = mavenProject.getDisplayName() + ": " + StringUtil.notNullize(task.getGoal()).trim();
        return MavenTasksLocalize.mavenTasksBeforeRun(desc);
    }

    @Nullable
    private MavenProject getMavenProject(MavenBeforeRunTask task) {
        String pomXmlPath = task.getProjectPath();
        if (StringUtil.isEmpty(pomXmlPath)) {
            return null;
        }

        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(pomXmlPath);
        if (file == null) {
            return null;
        }

        return MavenProjectsManager.getInstance(myProject).findProject(file);
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public MavenBeforeRunTask createTask(RunConfiguration runConfiguration) {
        return new MavenBeforeRunTask();
    }

    @Nonnull
    @RequiredUIAccess
    @Override
    public AsyncResult<Void> configureTask(RunConfiguration runConfiguration, MavenBeforeRunTask task) {
        MavenEditGoalDialog dialog = new MavenEditGoalDialog(myProject);

        dialog.setTitle(MavenTasksLocalize.mavenTasksSelectGoalTitle());

        if (task.getGoal() == null) {
            // just created empty task.
            MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
            List<MavenProject> rootProjects = projectsManager.getRootProjects();
            if (rootProjects.size() > 0) {
                dialog.setSelectedMavenProject(rootProjects.get(0));
            }
            else {
                dialog.setSelectedMavenProject(null);
            }
        }
        else {
            dialog.setGoals(task.getGoal());
            MavenProject mavenProject = getMavenProject(task);
            if (mavenProject != null) {
                dialog.setSelectedMavenProject(mavenProject);
            }
            else {
                dialog.setSelectedMavenProject(null);
            }
        }

        AsyncResult<Void> result = dialog.showAsync();
        result.doWhenDone(() -> {
            task.setProjectPath(dialog.getWorkDirectory() + "/pom.xml");
            task.setGoal(dialog.getGoals());
        });

        return result;
    }

    @Override
    public boolean canExecuteTask(RunConfiguration configuration, MavenBeforeRunTask task) {
        return task.getGoal() != null && task.getProjectPath() != null;
    }

    @Nonnull
    @Override
    public AsyncResult<Void> executeTaskAsync(
        UIAccess uiAccess,
        DataContext context,
        RunConfiguration configuration,
        ExecutionEnvironment env,
        MavenBeforeRunTask task
    ) {
        final Project project = context.getData(Project.KEY);
        final MavenProject mavenProject = getMavenProject(task);

        if (project == null || project.isDisposed() || mavenProject == null) {
            return AsyncResult.rejected();
        }

        final MavenExplicitProfiles explicitProfiles = MavenProjectsManager.getInstance(project).getExplicitProfiles();

        MavenRunnerParameters params = new MavenRunnerParameters(
            true,
            mavenProject.getDirectory(),
            ParametersListUtil.parse(task.getGoal()),
            explicitProfiles
        );

        RunnerAndConfigurationSettings configSettings =
            MavenRunConfigurationType.createRunnerAndConfigurationSettings(null, null, params, project);
        ProgramRunner runner = DelegateBuildRunner.getDelegateRunner();
        Executor executor = DefaultRunExecutor.getRunExecutorInstance();
        ExecutionEnvironment environment = new ExecutionEnvironment(executor, runner, configSettings, project);
        environment.setExecutionId(env.getExecutionId());
        MavenRunConfigurationType.setDelegate(environment);

        if (!runner.canRun(executor.getId(), environment.getRunProfile())) {
            return AsyncResult.rejected();
        }

        AsyncResult<Void> result = AsyncResult.undefined();

        uiAccess.give(() -> {
            FileDocumentManager.getInstance().saveAllDocuments();

            environment.setCallback(descriptor -> {
                ProcessHandler processHandler = descriptor != null ? descriptor.getProcessHandler() : null;
                if (processHandler != null) {
                    processHandler.addProcessListener(new ProcessListener() {
                        @Override
                        public void processTerminated(@Nonnull ProcessEvent event) {
                            if (event.getExitCode() == 0) {
                                result.setDone();
                            }
                            else {
                                result.setRejected();
                            }
                        }
                    });
                }
                else {
                    result.setRejected();
                }
            });

            try {
                runner.execute(environment);
            }
            catch (ExecutionException e) {
                result.setRejected();
            }
        }).doWhenRejectedWithThrowable(result::rejectWithThrowable);

        return result;
    }
}
