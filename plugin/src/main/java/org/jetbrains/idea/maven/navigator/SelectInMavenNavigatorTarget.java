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
package org.jetbrains.idea.maven.navigator;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.project.ui.view.SelectInContext;
import consulo.project.ui.view.SelectInTarget;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenLocalize;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

@ExtensionImpl
public class SelectInMavenNavigatorTarget implements SelectInTarget {
    @Override
    public boolean canSelect(SelectInContext context) {
        return getMavenProject(context) != null;
    }

    @Override
    @RequiredUIAccess
    public void selectIn(final SelectInContext context, boolean requestFocus) {
        Runnable r = () -> MavenProjectsNavigator.getInstance(context.getProject()).selectInTree(getMavenProject(context));
        if (requestFocus) {
            ToolWindowManager.getInstance(context.getProject()).getToolWindow(getToolWindowId()).activate(r);
        }
        else {
            r.run();
        }
    }

    private MavenProject getMavenProject(SelectInContext context) {
        VirtualFile file = context.getVirtualFile();
        MavenProjectsManager manager = MavenProjectsManager.getInstance(context.getProject());
        Module module = ProjectRootManager.getInstance(context.getProject()).getFileIndex().getModuleForFile(file);
        return module == null ? null : manager.findProject(module);
    }

    @Override
    public String getToolWindowId() {
        return MavenProjectsNavigator.TOOL_WINDOW_ID;
    }

    @Nonnull
    @Override
    public LocalizeValue getActionText() {
        return MavenLocalize.mavenName();
    }

    @Override
    public String getMinorViewId() {
        return null;
    }

    @Override
    public float getWeight() {
        return 20;
    }
}
