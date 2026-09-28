// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload;

import consulo.disposer.Disposable;
import consulo.externalSystem.autoimport.ExternalSystemProjectId;
import consulo.externalSystem.autoimport.ExternalSystemProjectTracker;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

public class MavenProfileWatcher {
    private final ExternalSystemProjectId myProjectId;
    private final ExternalSystemProjectTracker myProjectTracker;
    private final MavenProjectsManager myManager;

    public MavenProfileWatcher(ExternalSystemProjectId projectId, ExternalSystemProjectTracker projectTracker, MavenProjectsManager manager) {
        myProjectId = projectId;
        myProjectTracker = projectTracker;
        myManager = manager;
    }

    public void subscribeOnProfileChanges(Disposable parentDisposable) {
        myManager.addProjectsTreeListener(new MavenProjectsTree.Listener() {
            @Override
            public void profilesChanged() {
                myProjectTracker.markDirty(myProjectId);
                myProjectTracker.scheduleChangeProcessing();
            }
        }, parentDisposable);
    }
}
