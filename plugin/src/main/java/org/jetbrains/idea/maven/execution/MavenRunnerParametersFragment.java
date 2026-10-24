/*
 * Copyright 2013-2026 consulo.io
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
package org.jetbrains.idea.maven.execution;

import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;

/**
 * The working directory and the command line of a {@link MavenRunConfiguration}.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class MavenRunnerParametersFragment implements RunConfigurationFragment<MavenRunConfiguration> {
    @Override
    public String getId() {
        return "maven.parameters";
    }

    @Override
    public LocalizeValue getName() {
        return MavenRunnerLocalize.mavenRunnerParametersTitle();
    }

    @Override
    public int getOrder() {
        return StandardRunConfigurationFragmentOrder.FIRST;
    }

    @Override
    public boolean isRemovable() {
        return false;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<MavenRunConfiguration> createEditor(Project project, Disposable parent) {
        MavenRunnerParametersPanel panel = new MavenRunnerParametersPanel(project, false, false);

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                panel.addRows(layout);
            }

            @RequiredUIAccess
            @Override
            public void reset(MavenRunConfiguration target) {
                panel.getData(target.getRunnerParameters());
            }

            @RequiredUIAccess
            @Override
            public void apply(MavenRunConfiguration target) {
                MavenRunnerParameters parameters = target.getRunnerParameters();
                panel.setData(parameters);
                target.setRunnerParameters(parameters);
            }
        };
    }
}
