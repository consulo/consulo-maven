/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.logging.Logger;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@State(name = "MavenRunner", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class MavenRunner implements PersistentStateComponent<MavenRunnerSettings> {
    private static final Logger LOG = Logger.getInstance(MavenRunner.class);

    private MavenRunnerSettings mySettings = new MavenRunnerSettings();
    private final Project myProject;

    public static MavenRunner getInstance(Project project) {
        return project.getInstance(MavenRunner.class);
    }

    @Inject
    public MavenRunner(final Project project) {
        myProject = project;
    }

    public MavenRunnerSettings getSettings() {
        return mySettings;
    }

    @Override
    public MavenRunnerSettings getState() {
        return mySettings;
    }

    @Override
    public void loadState(MavenRunnerSettings settings) {
        mySettings = settings;
    }

    public void run(final MavenRunnerParameters parameters, final MavenRunnerSettings settings, final Runnable onComplete) {
        MavenRunConfigurationType.runConfiguration(
            myProject, parameters, null, settings,
            descriptor -> {
                if (descriptor == null) return;
                ProcessHandler handler = descriptor.getProcessHandler();
                if (handler == null) return;
                handler.addProcessListener(new ProcessAdapter() {
                    @Override
                    public void processTerminated(@Nonnull ProcessEvent event) {
                        if (event.getExitCode() == 0 && onComplete != null) {
                            onComplete.run();
                        }
                        updateTargetFolders();
                    }
                });
            }
        );
    }

    public boolean runBatch(
        List<MavenRunnerParameters> commands,
        @Nullable MavenGeneralSettings coreSettings,
        @Nullable MavenRunnerSettings runnerSettings,
        @Nullable final String action,
        @Nullable consulo.application.progress.ProgressIndicator indicator
    ) {
        LOG.assertTrue(!Application.get().isReadAccessAllowed());

        if (commands.isEmpty()) {
            return true;
        }

        try {
            int count = 0;
            for (MavenRunnerParameters command : commands) {
                if (indicator != null) {
                    indicator.setFraction(((double) count++) / commands.size());
                }

                CountDownLatch latch = new CountDownLatch(1);
                AtomicBoolean success = new AtomicBoolean(true);

                MavenRunConfigurationType.runConfiguration(
                    myProject, command, coreSettings, runnerSettings,
                    descriptor -> {
                        if (descriptor == null) {
                            success.set(false);
                            latch.countDown();
                            return;
                        }
                        ProcessHandler handler = descriptor.getProcessHandler();
                        if (handler == null) {
                            success.set(false);
                            latch.countDown();
                            return;
                        }
                        handler.addProcessListener(new ProcessAdapter() {
                            @Override
                            public void processTerminated(@Nonnull ProcessEvent event) {
                                success.set(event.getExitCode() == 0);
                                latch.countDown();
                            }
                        });
                        if (handler.isProcessTerminated()) {
                            latch.countDown();
                        }
                    },
                    true  // isDelegateBuild → Build ToolWindow
                );

                try {
                    latch.await();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }

                if (!success.get()) {
                    updateTargetFolders();
                    return false;
                }
            }

            updateTargetFolders();
        }
        finally {
            // nothing to close — Build ToolWindow manages its own lifecycle
        }

        return true;
    }

    private void updateTargetFolders() {
        if (myProject.isDisposed()) {
            return;
        }
        MavenProjectsManager.getInstance(myProject).updateProjectTargetFolders();
    }
}
