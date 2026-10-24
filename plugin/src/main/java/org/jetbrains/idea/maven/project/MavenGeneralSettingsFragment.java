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
package org.jetbrains.idea.maven.project;

import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.LabeledLayout;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;

/**
 * The general settings a {@link MavenRunConfiguration} uses instead of the project ones; hidden means the project settings.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
public class MavenGeneralSettingsFragment implements RunConfigurationFragment<MavenRunConfiguration> {
    @Override
    public String getId() {
        return "maven.generalSettings";
    }

    @Override
    public LocalizeValue getName() {
        return MavenRunnerLocalize.runConfigurationFragmentGeneralSettings();
    }

    @Override
    public boolean isVisibleByDefault(MavenRunConfiguration target) {
        return target.getOptions().getGeneralSettings() != null;
    }

    @Override
    public void applyHidden(MavenRunConfiguration target) {
        target.setGeneralSettings(null);
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<MavenRunConfiguration> createEditor(Project project, Disposable parent) {
        MavenGeneralPanel panel = new MavenGeneralPanel();
        LabeledLayout component = LabeledLayout.create(MavenProjectLocalize.mavenTabGeneral(), panel.createComponent(parent));

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addBottom(component);
            }

            @RequiredUIAccess
            @Override
            public void reset(MavenRunConfiguration target) {
                MavenGeneralSettings settings = target.getGeneralSettings();
                panel.getData(settings != null ? settings : MavenProjectsManager.getInstance(project).getGeneralSettings());
            }

            @RequiredUIAccess
            @Override
            public void apply(MavenRunConfiguration target) {
                MavenGeneralSettings settings = target.getGeneralSettings();
                if (settings == null) {
                    settings = MavenProjectsManager.getInstance(project).getGeneralSettings().clone();
                }
                panel.setData(settings);
                target.setGeneralSettings(settings);
            }
        };
    }
}
