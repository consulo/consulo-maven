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

import consulo.application.dumb.DumbAware;
import consulo.execution.ProgramRunnerUtil;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.RunnerRegistry;
import consulo.execution.executor.Executor;
import consulo.execution.executor.ExecutorRegistry;
import consulo.execution.runner.ProgramRunner;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Constraints;
import consulo.ui.ex.action.DefaultActionGroup;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import javax.annotation.Nonnull;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunConfigurationMenu extends DefaultActionGroup implements DumbAware {
    @Override
    public void update(AnActionEvent e) {
        for (AnAction action : getChildActionsOrStubs()) {
            if (action instanceof ExecuteMavenRunConfigurationAction) {
                remove(action);
            }
        }

        final Project project = e.getData(Project.KEY);

        final RunnerAndConfigurationSettings settings = e.getData(MavenDataKeys.RUN_CONFIGURATION);

        if (settings == null || project == null) {
            return;
        }

        Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
        for (int i = executors.length; --i >= 0; ) {
            final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executors[i].getId(), settings.getConfiguration());
            AnAction action = new ExecuteMavenRunConfigurationAction(executors[i], runner != null, project, settings);
            addAction(action, Constraints.FIRST);
        }

        super.update(e);
    }

    private static class ExecuteMavenRunConfigurationAction extends AnAction {
        private final Executor myExecutor;
        private final boolean myEnabled;
        private final Project myProject;
        private final RunnerAndConfigurationSettings mySettings;

        public ExecuteMavenRunConfigurationAction(
            Executor executor,
            boolean enabled,
            Project project,
            RunnerAndConfigurationSettings settings
        ) {
            super(executor.getActionName(), null, executor.getIcon());
            myExecutor = executor;
            myEnabled = enabled;
            myProject = project;
            mySettings = settings;
        }

        @Override
        public void actionPerformed(@Nonnull AnActionEvent event) {
            if (myEnabled) {
                ProgramRunnerUtil.executeConfiguration(mySettings, myExecutor);
            }
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            super.update(e);
            e.getPresentation().setEnabled(myEnabled);
        }
    }
}
