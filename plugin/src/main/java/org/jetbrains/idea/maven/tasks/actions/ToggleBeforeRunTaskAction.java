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

import consulo.dataContext.DataContext;
import consulo.execution.RunManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.lang.Pair;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTask;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTasksProvider;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;

import java.util.List;

public class ToggleBeforeRunTaskAction extends MavenToggleAction {
    @Override
    protected boolean isAvailable(AnActionEvent e) {
        return super.isAvailable(e) && getTaskDesc(e.getDataContext()) != null;
    }

    @Override
    protected boolean doIsSelected(AnActionEvent e) {
        final DataContext context = e.getDataContext();
        final Pair<MavenProject, String> desc = getTaskDesc(context);
        if (desc != null) {
            for (MavenBeforeRunTask each : getRunManager(context).getBeforeRunTasks(MavenBeforeRunTasksProvider.ID)) {
                if (each.isFor(desc.first, desc.second)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @RequiredUIAccess
    public void setSelected(final AnActionEvent e, boolean state) {
        final DataContext context = e.getDataContext();
        final Pair<MavenProject, String> desc = getTaskDesc(context);
        if (desc != null) {
            new MavenExecuteBeforeRunDialog(MavenActionUtil.getProject(context), desc.first, desc.second).show();
        }
    }

    @Nullable
    protected static Pair<MavenProject, String> getTaskDesc(DataContext context) {
        List<String> goals = context.getData(MavenDataKeys.MAVEN_GOALS);
        if (goals == null || goals.size() != 1) {
            return null;
        }

        MavenProject mavenProject = MavenActionUtil.getMavenProject(context);
        if (mavenProject == null) {
            return null;
        }

        return Pair.create(mavenProject, goals.get(0));
    }

    private static RunManager getRunManager(DataContext context) {
        return RunManager.getInstance(MavenActionUtil.getProject(context));
    }
}
