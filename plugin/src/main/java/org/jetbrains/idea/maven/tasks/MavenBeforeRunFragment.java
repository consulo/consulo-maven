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
import consulo.disposer.Disposable;
import consulo.document.FileDocumentManager;
import consulo.execution.BeforeRunFragment;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
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
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerParametersPanel;
import org.jetbrains.idea.maven.execution.build.DelegateBuildRunner;
import org.jetbrains.idea.maven.localize.MavenTasksLocalize;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

@ExtensionImpl
public class MavenBeforeRunFragment implements BeforeRunFragment<MavenBeforeRunTask> {
    public static final String ID = "Maven.BeforeRunTask";

    private final Project myProject;

    @Inject
    public MavenBeforeRunFragment(Project project) {
        myProject = project;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LocalizeValue getName() {
        return MavenTasksLocalize.mavenTasksBeforeRunEmpty();
    }

    @Override
    public @Nullable Image getIcon(RunConfiguration configuration) {
        return MavenIconGroup.mavenlogo();
    }

    @Override
    public LocalizeValue getDescription(MavenBeforeRunTask task) {
        MavenProject mavenProject = getMavenProject(task);
        if (mavenProject == null) {
            return MavenTasksLocalize.mavenTasksBeforeRunEmpty();
        }

        String desc = mavenProject.getDisplayName() + ": " + StringUtil.notNullize(task.getGoal()).trim();
        return MavenTasksLocalize.mavenTasksBeforeRun(desc);
    }

    @Override
    public Class<MavenBeforeRunTask> getStateClass() {
        return MavenBeforeRunTask.class;
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

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<MavenBeforeRunTask> createEditor(RunConfiguration configuration, Disposable parent) {
        MavenRunnerParametersPanel panel = new MavenRunnerParametersPanel(myProject, false, false);

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                panel.addRows(layout);
            }

            @RequiredUIAccess
            @Override
            public void reset(MavenBeforeRunTask task) {
                MavenProject mavenProject;
                if (task.getGoal() == null) {
                    List<MavenProject> rootProjects = MavenProjectsManager.getInstance(myProject).getRootProjects();
                    mavenProject = rootProjects.isEmpty() ? null : rootProjects.get(0);
                }
                else {
                    panel.getGoalsEditor().setValue(task.getGoal());
                    mavenProject = getMavenProject(task);
                }
                panel.getWorkingDirectory().setValue(mavenProject == null ? "" : mavenProject.getDirectory());
            }

            @RequiredUIAccess
            @Override
            public void apply(MavenBeforeRunTask task) {
                String workingDirectory = panel.getWorkingDirectory().getValue();
                task.setProjectPath(StringUtil.isEmptyOrSpaces(workingDirectory) ? null : workingDirectory + "/pom.xml");
                task.setGoal(panel.getGoalsEditor().getValue());
            }
        };
    }

    @Override
    public boolean canExecuteTask(RunConfiguration configuration, MavenBeforeRunTask task) {
        return task.getGoal() != null && task.getProjectPath() != null;
    }

    @Override
    public CompletableFuture<?> executeAsync(
        UIAccess uiAccess,
        DataContext context,
        RunConfiguration configuration,
        ExecutionEnvironment env,
        MavenBeforeRunTask task
    ) {
        final Project project = context.getData(Project.KEY);
        final MavenProject mavenProject = getMavenProject(task);

        if (project == null || project.isDisposed() || mavenProject == null) {
            return CompletableFuture.failedFuture(new CancellationException("project not found"));
        }

        final MavenExplicitProfiles explicitProfiles = MavenProjectsManager.getInstance(project).getExplicitProfiles();

        MavenRunnerParameters params = new MavenRunnerParameters(
            true,
            mavenProject.getDirectory(),
            ParametersListUtil.parse(StringUtil.notNullize(task.getGoal())),
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
            return CompletableFuture.failedFuture(new CancellationException("can't run"));
        }

        CompletableFuture<Void> result = new CompletableFuture<>();

        uiAccess.give(() -> {
            FileDocumentManager.getInstance().saveAllDocuments();

            environment.setCallback(descriptor -> {
                ProcessHandler processHandler = descriptor != null ? descriptor.getProcessHandler() : null;
                if (processHandler != null) {
                    processHandler.addProcessListener(new ProcessListener() {
                        @Override
                        public void processTerminated(@Nonnull ProcessEvent event) {
                            if (event.getExitCode() == 0) {
                                result.complete(null);
                            }
                            else {
                                result.completeExceptionally(new CancellationException());
                            }
                        }
                    });
                }
                else {
                    result.completeExceptionally(new CancellationException());
                }
            });

            try {
                runner.execute(environment);
            }
            catch (ExecutionException e) {
                result.completeExceptionally(e);
            }
        });

        return result;
    }
}
