// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;
import consulo.module.Module;
import consulo.project.Project;

import java.util.Collection;
import java.util.List;

@TopicAPI(ComponentScope.APPLICATION)
public interface MavenSyncListener {
    /**
     * Called when Maven sync is started
     */
    default void syncStarted(Project project) {
    }

    /**
     * Called when Maven sync is finished
     */
    default void syncFinished(Project project) {
    }

    /**
     * Called when Maven model is collected and IDEA is ready to import Maven model into its own Workspace model
     */
    default void importStarted(Project project) {
    }

    /**
     * Workspace model is committed, project structure is created. Please note, that certain related activities
     * may not be completed yet, e.g., plugin resolution and source downloading
     */
    default void importFinished(Project project, Collection<MavenProject> importedProjects, List<Module> newModules) {
    }
}
