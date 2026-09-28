// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload;

import consulo.application.progress.ProgressManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.document.FileDocumentManager;
import consulo.externalSystem.autoimport.ExternalSystemProjectAware;
import consulo.externalSystem.autoimport.ExternalSystemProjectId;
import consulo.externalSystem.autoimport.ExternalSystemProjectListener;
import consulo.externalSystem.autoimport.ExternalSystemProjectReloadContext;
import consulo.externalSystem.autoimport.ExternalSystemRefreshStatus;
import consulo.externalSystem.autoimport.ExternalSystemSettingsFilesReloadContext;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.project.Project;
import consulo.util.collection.Lists;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenSyncListener;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class MavenProjectAware implements ExternalSystemProjectAware {
    private final Project myProject;
    private final ExternalSystemProjectId myProjectId;
    private final MavenProjectsManager myManager;

    private final AtomicBoolean myIsSyncCompleted = new AtomicBoolean(true);
    private final List<ExternalSystemProjectListener> myListeners = Lists.newLockFreeCopyOnWriteList();

    public MavenProjectAware(Project project, ExternalSystemProjectId projectId, MavenProjectsManager manager) {
        myProject = project;
        myProjectId = projectId;
        myManager = manager;

        project.getApplication().getMessageBus().connect(manager).subscribe(MavenSyncListener.class, new MavenSyncListener() {
            @Override
            public void syncFinished(Project project) {
                if (myProject == project) {
                    setSyncCompleted(true);
                }
            }

            @Override
            public void syncStarted(Project project) {
                if (myProject == project) {
                    setSyncCompleted(false);
                }
            }
        });
    }

    @Override
    public ExternalSystemProjectId getProjectId() {
        return myProjectId;
    }

    @Override
    public Set<String> getSettingsFiles() {
        return collectSettingsFiles();
    }

    @Override
    public void subscribe(ExternalSystemProjectListener listener, Disposable parentDisposable) {
        myListeners.add(listener);
        Disposer.register(parentDisposable, () -> myListeners.remove(listener));
    }

    private void setSyncCompleted(boolean completed) {
        boolean oldValue = myIsSyncCompleted.getAndSet(completed);
        if (!oldValue && completed) {
            for (ExternalSystemProjectListener listener : myListeners) {
                listener.onProjectReloadFinish(ExternalSystemRefreshStatus.SUCCESS);
            }
        }
        else if (oldValue && !completed) {
            for (ExternalSystemProjectListener listener : myListeners) {
                listener.onProjectReloadStart();
            }
        }
    }

    @Override
    public void reloadProject(ExternalSystemProjectReloadContext context) {
        MavenLog.LOG.debug("MavenProjectAware.reloadProject");
        myProject.getUIAccess().giveAndWaitIfNeed(() -> FileDocumentManager.getInstance().saveAllDocuments());

        if (context.hasUndefinedModifications()) {
            MavenLog.LOG.debug("MavenProjectAware.reloadProject - context.hasUndefinedModifications=true");
            MavenSyncSpec spec =
                MavenSyncSpec.incremental("MavenProjectAware.reloadProject, undefined modifications", context.isExplicitReload());
            myManager.scheduleUpdateAllMavenProjects(spec);
        }
        else {
            MavenLog.LOG.debug("MavenProjectAware.reloadProject - context.hasUndefinedModifications=false");
            ExternalSystemSettingsFilesReloadContext settingsFilesContext = context.getSettingsFilesContext();
            List<VirtualFile> filesToUpdate = new ArrayList<>();
            List<VirtualFile> filesToDelete = new ArrayList<>();
            for (VirtualFile projectsFile : myManager.getProjectsFiles()) {
                String path = projectsFile.getPath();
                if (settingsFilesContext.getCreated().contains(path)) {
                    filesToUpdate.add(projectsFile);
                }
                if (settingsFilesContext.getUpdated().contains(path)) {
                    filesToUpdate.add(projectsFile);
                }
                if (settingsFilesContext.getDeleted().contains(path)) {
                    filesToDelete.add(projectsFile);
                }
            }
            Set<String> updated = new HashSet<>(settingsFilesContext.getCreated());
            updated.addAll(settingsFilesContext.getUpdated());
            Set<String> deleted = settingsFilesContext.getDeleted();
            if (updated.size() == filesToUpdate.size() && deleted.size() == filesToDelete.size()) {
                MavenSyncSpec spec = MavenSyncSpec.incremental("MavenProjectAware.reloadProject, sync selected", context.isExplicitReload());
                myManager.scheduleUpdateMavenProjects(spec, filesToUpdate, filesToDelete);
            }
            else {
                MavenSyncSpec spec = MavenSyncSpec.incremental("MavenProjectAware.reloadProject, sync all", context.isExplicitReload());
                myManager.scheduleUpdateAllMavenProjects(spec);
            }
        }
    }

    private Set<String> collectSettingsFiles() {
        Set<String> result = new LinkedHashSet<>(myManager.getState().originalFiles);
        for (VirtualFile projectsFile : myManager.getProjectsFiles()) {
            result.add(projectsFile.getPath());
        }
        for (MavenProject mavenProject : myManager.getProjects()) {
            ProgressManager.checkCanceled();

            result.addAll(mavenProject.getModulePaths());
            String rootDirectory = mavenProject.getDirectory();
            result.add(rootDirectory + "/" + MavenConstants.JVM_CONFIG_RELATIVE_PATH);
            result.add(rootDirectory + "/" + MavenConstants.MAVEN_CONFIG_RELATIVE_PATH);
            result.add(rootDirectory + "/" + MavenConstants.MAVEN_WRAPPER_RELATIVE_PATH);
            result.add(rootDirectory + "/" + MavenConstants.PROFILES_XML);
        }

        Set<String> systemIndependent = new LinkedHashSet<>();
        for (String path : result) {
            systemIndependent.add(FileUtil.toSystemIndependentName(path));
        }
        return systemIndependent;
    }
}
