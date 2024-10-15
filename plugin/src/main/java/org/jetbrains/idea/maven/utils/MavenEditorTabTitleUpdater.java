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

package org.jetbrains.idea.maven.utils;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.fileEditor.FileEditorManager;
import consulo.project.Project;
import consulo.util.lang.Pair;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.util.List;

@Singleton
@ServiceAPI(value = ComponentScope.PROJECT, lazy = false)
@ServiceImpl
public class MavenEditorTabTitleUpdater extends MavenSimpleProjectComponent {
    @Inject
    public MavenEditorTabTitleUpdater(Project project) {
        super(project);

        if (!isNormalProject()) {
            return;
        }

        MavenProjectsManager.getInstance(myProject).addProjectsTreeListener(new MavenProjectsTree.Listener() {
            @Override
            public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
                updateTabName(MavenUtil.collectFirsts(updated));
            }
        });
    }

    private void updateTabName(final List<MavenProject> projects) {
        MavenUtil.invokeLater(
            myProject,
            () -> {
                for (MavenProject each : projects) {
                    FileEditorManager.getInstance(myProject).updateFilePresentation(each.getFile());
                }
            }
        );
    }
}
