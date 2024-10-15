/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.navigator.actions;

import consulo.execution.RunManager;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.ide.impl.idea.execution.impl.EditConfigurationsDialog;
import consulo.ui.ex.action.AnActionEvent;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import jakarta.annotation.Nonnull;

/**
 * @author Sergey Evdokimov
 */
public class EditMavenRunConfigurationAction extends AnAction {
    @RequiredUIAccess
    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        RunnerAndConfigurationSettings settings = e.getData(MavenDataKeys.RUN_CONFIGURATION);

        assert settings != null && project != null;

        RunManager.getInstance(project).setSelectedConfiguration(settings);

        EditConfigurationsDialog dialog = new EditConfigurationsDialog(project);
        dialog.show();
    }

    @RequiredUIAccess
    @Override
    public void update(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        RunnerAndConfigurationSettings settings = e.getData(MavenDataKeys.RUN_CONFIGURATION);

        boolean enabled = settings != null && project != null;
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}
