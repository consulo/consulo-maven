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
package org.jetbrains.idea.maven.project.actions;

import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserFactory;
import consulo.language.editor.PlatformDataKeys;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;


public class AddManagedFilesAction extends MavenAction {
    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        final MavenProjectsManager manager = MavenActionUtil.getProjectsManager(e.getDataContext());
        FileChooserDescriptor singlePomSelection = new FileChooserDescriptor(true, false, false, false, false, true) {
            @Override
            @RequiredUIAccess
            public boolean isFileSelectable(VirtualFile file) {
                return super.isFileSelectable(file) && !manager.isManagedFile(file);
            }

            @Override
            public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
                return (file.isDirectory() || MavenActionUtil.isMavenProjectFile(file))
                    && super.isFileVisible(file, showHiddenFiles);
            }
        };

        Project project = MavenActionUtil.getProject(e.getDataContext());
        VirtualFile fileToSelect = e.getData(PlatformDataKeys.VIRTUAL_FILE);

        FileChooserFactory.getInstance()
            .newBuilder(singlePomSelection)
            .project(project)
            .toSelect(fileToSelect == null ? null : fileToSelect.getFileSystem().getNioPath(fileToSelect))
            .showMultipleAsVirtualFiles()
            .whenComplete((files, error) -> {
                if (error == null && !files.isEmpty()) {
                    manager.addManagedFiles(files);
                }
            });
    }
}
