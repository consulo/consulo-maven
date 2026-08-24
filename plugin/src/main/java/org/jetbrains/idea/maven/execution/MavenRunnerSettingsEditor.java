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
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenDisablePanelCheckbox;

import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunnerSettingsEditor extends SettingsEditor<MavenRunConfiguration> {
    private final MavenRunnerPanel myPanel;

    private JCheckBox myUseProjectSettings;

    public MavenRunnerSettingsEditor(@Nonnull Project project) {
        myPanel = new MavenRunnerPanel(project, true);
    }

    @RequiredUIAccess
    @Override
    protected void resetEditorFrom(MavenRunConfiguration runConfiguration) {
        myUseProjectSettings.setSelected(runConfiguration.getRunnerSettings() == null);

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
        if (myUseProjectSettings.isSelected()) {
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

    @Nonnull
    @Override
    @RequiredUIAccess
    protected JComponent createEditor() {
        // TODO MavenDisablePanelCheckbox is still swing - it walks the component tree to disable it
        JComponent panel = (JComponent)TargetAWT.to(myPanel.createUIComponent(this));

        Pair<JPanel, JCheckBox> pair =
            MavenDisablePanelCheckbox.createPanel(panel, MavenRunnerLocalize.mavenRunnerUseProjectSettings().get());

        myUseProjectSettings = pair.second;
        return pair.first;
    }
}
