/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ide.setting.ShowSettingsUtil;
import org.jetbrains.idea.maven.utils.MavenSettings;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

public class EditSettingsAction extends MavenAction {
    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        showSettingsFor(MavenActionUtil.getProject(e.getDataContext()));
    }

    @RequiredUIAccess
    protected static void showSettingsFor(Project project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, MavenSettings.DISPLAY_NAME);
    }
}