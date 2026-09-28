// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload;

import consulo.disposer.Disposable;
import consulo.externalSystem.autoimport.ExternalSystemModificationType;
import consulo.externalSystem.autoimport.ExternalSystemProjectTracker;
import consulo.externalSystem.autoimport.Stamp;
import consulo.externalSystem.autoimport.changes.AsyncFileChangesListener;
import consulo.externalSystem.autoimport.changes.FilesChangesListener;
import consulo.externalSystem.autoimport.settings.AsyncSupplier;
import consulo.externalSystem.autoimport.settings.BackgroundAsyncSupplier;
import consulo.util.io.FileUtil;
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class MavenGeneralSettingsWatcher {
    private final MavenProjectsManager myManager;
    private final ExecutorService myBackgroundExecutor;

    public MavenGeneralSettingsWatcher(MavenProjectsManager manager, ExecutorService backgroundExecutor) {
        myManager = manager;
        myBackgroundExecutor = backgroundExecutor;
    }

    private MavenGeneralSettings getGeneralSettings() {
        return myManager.getGeneralSettings();
    }

    private MavenEmbeddersManager getEmbeddersManager() {
        return myManager.getEmbeddersManager();
    }

    private Set<String> collectSettingsFiles() {
        Set<String> result = new LinkedHashSet<>();
        File userSettingsFile = MavenUtil.resolveUserSettingsFile(getGeneralSettings().getUserSettingsFile());
        String canonicalPath = FileUtil.toCanonicalPath(userSettingsFile.getAbsolutePath());
        if (canonicalPath != null) {
            result.add(canonicalPath);
        }
        return result;
    }

    private void fireSettingsChange() {
        getEmbeddersManager().reset();
        myManager.scheduleUpdateAllMavenProjects(MavenSyncSpec.full("MavenGeneralSettingsWatcher.fireSettingsChange"));
    }

    private void fireSettingsXmlChange() {
        getGeneralSettings().changed();
        // fireSettingsChange() will be called indirectly by pathsChanged listener on GeneralSettings object
    }

    public void subscribeOnSettingsChanges(Disposable parentDisposable) {
        getGeneralSettings().addListener(this::fireSettingsChange, parentDisposable);
    }

    public void subscribeOnSettingsFileChanges(Disposable parentDisposable) {
        BackgroundAsyncSupplier<Set<String>> filesProvider = new BackgroundAsyncSupplier<>(
            AsyncSupplier.blocking(this::collectSettingsFiles),
            ExternalSystemProjectTracker::isAsyncChangesProcessing,
            myBackgroundExecutor,
            parentDisposable
        );
        AsyncFileChangesListener.subscribeOnVirtualFilesChanges(false, filesProvider, new FilesChangesListener() {
            @Override
            public void onFileChange(Stamp stamp, String path, long modificationStamp, ExternalSystemModificationType modificationType) {
                String fileChangeMessage = "File change: " + path + ", " + modificationStamp + ", " + modificationType;
                MavenLog.LOG.debug(fileChangeMessage);
            }

            @Override
            public void apply() {
                fireSettingsXmlChange();
            }
        }, parentDisposable);
    }
}
