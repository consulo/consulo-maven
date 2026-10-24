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
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.LabeledLayout;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;

/**
 * The runner settings a {@link MavenRunConfiguration} uses instead of the project ones; hidden means the project settings.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class MavenRunnerSettingsFragment implements RunConfigurationFragment<MavenRunConfiguration> {
    @Override
    public String getId() {
        return "maven.runnerSettings";
    }

    @Override
    public LocalizeValue getName() {
        return MavenRunnerLocalize.runConfigurationFragmentRunnerSettings();
    }

    @Override
    public boolean isVisibleByDefault(MavenRunConfiguration target) {
        return target.getOptions().getRunnerSettings() != null;
    }

    @Override
    public void applyHidden(MavenRunConfiguration target) {
        target.setRunnerSettings(null);
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<MavenRunConfiguration> createEditor(Project project, Disposable parent) {
        MavenRunnerPanel panel = new MavenRunnerPanel(project, true);
        LabeledLayout component = LabeledLayout.create(MavenRunnerLocalize.mavenTabRunner(), panel.createUIComponent(parent));

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addBottom(component);
            }

            @RequiredUIAccess
            @Override
            public void reset(MavenRunConfiguration target) {
                MavenRunnerSettings settings = target.getRunnerSettings();
                panel.reset(settings != null ? settings : MavenRunner.getInstance(project).getSettings());
            }

            @RequiredUIAccess
            @Override
            public void apply(MavenRunConfiguration target) {
                MavenRunnerSettings settings = target.getRunnerSettings();
                if (settings == null) {
                    settings = MavenRunner.getInstance(project).getSettings().clone();
                }
                panel.apply(settings);
                target.setRunnerSettings(settings);
            }
        };
    }
}
