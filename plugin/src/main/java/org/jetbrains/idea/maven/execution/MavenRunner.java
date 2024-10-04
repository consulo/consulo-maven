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
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.component.ProcessCanceledException;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.document.FileDocumentManager;
import consulo.ide.ServiceManager;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenConsoleImpl;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@State(name = "MavenRunner", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class MavenRunner implements PersistentStateComponent<MavenRunnerSettings> {
    private static final Logger LOG = Logger.getInstance(MavenRunner.class);

    private MavenRunnerSettings mySettings = new MavenRunnerSettings();
    private final Project myProject;

    public static MavenRunner getInstance(Project project) {
        return ServiceManager.getService(project, MavenRunner.class);
    }

    @Inject
    public MavenRunner(final Project project) {
        myProject = project;
    }

    public MavenRunnerSettings getSettings() {
        return mySettings;
    }

    public MavenRunnerSettings getState() {
        return mySettings;
    }

    public void loadState(MavenRunnerSettings settings) {
        mySettings = settings;
    }

    public void run(final MavenRunnerParameters parameters, final MavenRunnerSettings settings, final Runnable onComplete) {
        FileDocumentManager.getInstance().saveAllDocuments();

        final MavenConsole console = createConsole();
        try {
            final MavenExecutor[] executor = new MavenExecutor[]{createExecutor(parameters, null, settings, console)};

            ProgressManager.getInstance().run(new Task.Backgroundable(myProject, executor[0].getCaption(), true) {
                public void run(@Nonnull ProgressIndicator indicator) {
                    try {
                        try {
                            if (executor[0].execute(indicator) && onComplete != null) {
                                onComplete.run();
                            }
                        }
                        catch (ProcessCanceledException ignore) {
                        }

                        executor[0] = null;
                        updateTargetFolders();
                    }
                    finally {
                        console.finish();
                    }
                }

                @Nullable
                public NotificationInfo getNotificationInfo() {
                    return new NotificationInfo("Maven", "Maven Task Finished", "");
                }

                public boolean shouldStartInBackground() {
                    return settings.isRunMavenInBackground();
                }

                public void processSentToBackground() {
                    settings.setRunMavenInBackground(true);
                }

                public void processRestoredToForeground() {
                    settings.setRunMavenInBackground(false);
                }
            });
        }
        catch (Exception e) {
            console.printException(e);
            console.finish();
            MavenLog.LOG.warn(e);
        }
    }

    public boolean runBatch(
        List<MavenRunnerParameters> commands,
        @Nullable MavenGeneralSettings coreSettings,
        @Nullable MavenRunnerSettings runnerSettings,
        @Nullable final String action,
        @Nullable ProgressIndicator indicator
    ) {
        LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed());

        if (commands.isEmpty()) {
            return true;
        }

        MavenConsole console = ReadAction.compute(() -> myProject.isDisposed() ? null : createConsole());

        if (console == null) {
            return false;
        }

        try {
            int count = 0;
            for (MavenRunnerParameters command : commands) {
                if (indicator != null) {
                    indicator.setFraction(((double)count++) / commands.size());
                }

                MavenExecutor executor = ReadAction.compute(() -> myProject.isDisposed() ? null : createExecutor(command,
                    coreSettings,
                    runnerSettings,
                    console
                ));

                if (executor == null) {
                    break;
                }

                executor.setAction(action);
                if (!executor.execute(indicator)) {
                    updateTargetFolders();
                    return false;
                }
            }

            updateTargetFolders();
        }
        finally {
            console.finish();
        }

        return true;
    }

    private void updateTargetFolders() {
        if (myProject.isDisposed()) {
            return; // project was closed before task finished.
        }
        MavenProjectsManager.getInstance(myProject).updateProjectTargetFolders();
    }

    private MavenConsole createConsole() {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            return new SoutMavenConsole();
        }
        return new MavenConsoleImpl("Maven Goal", myProject);
    }

    private MavenExecutor createExecutor(
        MavenRunnerParameters taskParameters,
        @Nullable MavenGeneralSettings coreSettings,
        @Nullable MavenRunnerSettings runnerSettings,
        MavenConsole console
    ) {
        return new MavenExternalExecutor(myProject, taskParameters, coreSettings, runnerSettings, console);
    }
}
