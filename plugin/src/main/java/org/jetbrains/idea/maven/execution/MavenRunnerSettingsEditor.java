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
package org.jetbrains.idea.maven.execution;

import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenDisablePanelCheckbox;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunnerSettingsEditor extends SettingsEditor<MavenRunConfiguration> {
    private final MavenRunnerPanel myPanel;

    private final CheckBox myUseProjectSettings;

    @RequiredUIAccess
    public MavenRunnerSettingsEditor(Project project) {
        myPanel = new MavenRunnerPanel(project, true);
        myUseProjectSettings = CheckBox.create(MavenRunnerLocalize.mavenRunnerUseProjectSettings());
    }

    @RequiredUIAccess
    @Override
    protected void resetEditorFrom(MavenRunConfiguration runConfiguration) {
        myUseProjectSettings.setValue(runConfiguration.getRunnerSettings() == null);

        if (runConfiguration.getRunnerSettings() == null) {
            MavenRunnerSettings settings = MavenRunner.getInstance(myPanel.getProject()).getSettings();
            myPanel.reset(settings);
        }
        else {
            myPanel.reset(runConfiguration.getRunnerSettings());
        }
    }

    @RequiredUIAccess
    @Override
    protected void applyEditorTo(MavenRunConfiguration runConfiguration) throws ConfigurationException {
        if (myUseProjectSettings.getValueOrError()) {
            runConfiguration.setRunnerSettings(null);
        }
        else {
            if (runConfiguration.getRunnerSettings() != null) {
                myPanel.apply(runConfiguration.getRunnerSettings());
            }
            else {
                MavenRunnerSettings settings = MavenRunner.getInstance(myPanel.getProject()).getSettings().clone();
                myPanel.apply(settings);
                runConfiguration.setRunnerSettings(settings);
            }
        }
    }

    @Override
    @RequiredUIAccess
    protected Component createUIComponent() {
        return MavenDisablePanelCheckbox.createPanel(myPanel.createUIComponent(this), myUseProjectSettings);
    }
}
