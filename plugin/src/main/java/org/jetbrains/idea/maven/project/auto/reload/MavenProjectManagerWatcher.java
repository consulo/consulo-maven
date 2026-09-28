// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload;

import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.externalSystem.autoimport.ExternalSystemProjectId;
import consulo.externalSystem.autoimport.ExternalSystemProjectTracker;
import consulo.module.Module;
import consulo.module.event.ModuleListener;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MavenProjectManagerWatcher {
    private final Project myProject;
    private final MavenProjectAware myProjectAware;
    private final MavenProfileWatcher myProfileWatcher;
    private final MavenRenameModuleWatcher myRenameModuleWatcher;
    private final MavenGeneralSettingsWatcher myGeneralSettingsWatcher;
    private final Disposable myDisposable;
    private final AtomicBoolean myStarted = new AtomicBoolean(false);

    public MavenProjectManagerWatcher(Project project) {
        myProject = project;
        ExecutorService backgroundExecutor =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("MavenProjectsManagerWatcher.backgroundExecutor", 1);
        MavenProjectsManager projectManager = MavenProjectsManager.getInstance(myProject);
        ExternalSystemProjectTracker projectTracker = ExternalSystemProjectTracker.getInstance(myProject);
        ExternalSystemProjectId projectId = new ExternalSystemProjectId(MavenUtil.SYSTEM_ID, myProject.getName());
        myProjectAware = new MavenProjectAware(project, projectId, projectManager);
        myProfileWatcher = new MavenProfileWatcher(projectId, projectTracker, projectManager);
        myRenameModuleWatcher = new MavenRenameModuleWatcher();
        myGeneralSettingsWatcher = new MavenGeneralSettingsWatcher(projectManager, backgroundExecutor);
        myDisposable = Disposable.newDisposable(MavenProjectManagerWatcher.class.toString());
        Disposer.register(projectManager, myDisposable);
    }

    public synchronized void start() {
        if (!myStarted.compareAndSet(false, true)) {
            MavenLog.LOG.error("Trying to start the watcher one more time", new Exception());
            return;
        }
        myProject.getMessageBus().connect(myDisposable).subscribe(ModuleListener.class, myRenameModuleWatcher);
        myProject.getMessageBus().connect(myDisposable).subscribe(ModuleListener.class, new ModuleIgnoredStateWatcher());
        myGeneralSettingsWatcher.subscribeOnSettingsChanges(myDisposable);
        myGeneralSettingsWatcher.subscribeOnSettingsFileChanges(myDisposable);
        MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
        ExternalSystemProjectTracker projectTracker = ExternalSystemProjectTracker.getInstance(myProject);
        projectTracker.register(myProjectAware, projectsManager);
        projectTracker.activate(myProjectAware.getProjectId());
        myProfileWatcher.subscribeOnProfileChanges(myDisposable);
    }

    @TestOnly
    public synchronized void enableAutoImportInTests() {
        ExternalSystemProjectTracker.enableAutoReloadInTests(myDisposable);
    }

    public synchronized void stop() {
        Disposer.dispose(myDisposable);
    }

    private final class ModuleIgnoredStateWatcher implements ModuleListener {
        @Override
        public void moduleRemoved(Project project, Module module) {
            MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);
            MavenProject mavenProject = manager.findProject(module);
            if (mavenProject != null && !manager.isIgnored(mavenProject)) {
                VirtualFile file = mavenProject.getFile();

                if (manager.isManagedFile(file) && manager.getModules(mavenProject).isEmpty()) {
                    manager.removeManagedFiles(Collections.singletonList(file));
                }
                else {
                    manager.setIgnoredState(Collections.singletonList(mavenProject), true);
                }
            }
        }

        @Override
        public void moduleAdded(Project project, Module module) {
            MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);
            if (manager.isMavenizedModule(module)) {
                MavenProject mavenProject = manager.findProject(module);
                if (mavenProject != null) {
                    manager.setIgnoredState(Collections.singletonList(mavenProject), false);
                }
            }
        }
    }
}
