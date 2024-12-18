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
package org.jetbrains.idea.maven.tasks.actions;

import java.util.List;

import jakarta.annotation.Nullable;

import consulo.dataContext.DataContext;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ide.impl.idea.openapi.keymap.impl.ui.EditKeymapsDialog;

public class AssignShortcutAction extends MavenAction {
    @Override
    protected boolean isAvailable(AnActionEvent e) {
        final DataContext context = e.getDataContext();
        return super.isAvailable(e) && !isIgnoredProject(context) && getGoalActionId(context) != null;
    }

    private static boolean isIgnoredProject(DataContext context) {
        final MavenProject project = MavenActionUtil.getMavenProject(context);
        return project != null && MavenActionUtil.getProjectsManager(context).isIgnored(project);
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        final DataContext context = e.getDataContext();
        String actionId = getGoalActionId(context);
        if (actionId != null) {
            new EditKeymapsDialog(MavenActionUtil.getProject(context), actionId).show();
        }
    }

    @Nullable
    private static String getGoalActionId(DataContext context) {
        final List<String> goals = context.getData(MavenDataKeys.MAVEN_GOALS);
        if (goals == null || goals.size() != 1) {
            return null;
        }

        MavenProject project = MavenActionUtil.getMavenProject(context);
        if (project == null) {
            return null;
        }

        String goal = goals.get(0);

        return getShortcutsManager(context).getActionId(project.getPath(), goal);
    }

    protected static MavenShortcutsManager getShortcutsManager(DataContext context) {
        return MavenShortcutsManager.getInstance(MavenActionUtil.getProject(context));
    }
}
