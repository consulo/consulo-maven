/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.configurable.UnnamedConfigurable;
import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.annotations.Nls;
import org.jetbrains.idea.maven.server.MavenServerManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MavenImportingConfigurable implements SearchableConfigurable {
    private final MavenImportingSettings myImportingSettings;
    private final MavenImportingSettingsForm mySettingsForm = new MavenImportingSettingsForm(false, false);
    private final List<UnnamedConfigurable> myAdditionalConfigurables;

    private final JTextField myEmbedderVMOptions;

    public MavenImportingConfigurable(Project project) {
        myImportingSettings = MavenProjectsManager.getInstance(project).getImportingSettings();

        myAdditionalConfigurables = new ArrayList<>();
        for (final AdditionalMavenImportingSettings additionalSettings : AdditionalMavenImportingSettings.EP_NAME.getExtensionList()) {
            myAdditionalConfigurables.add(additionalSettings.createConfigurable(project));
        }

        myEmbedderVMOptions = new JTextField(30);
    }

    @RequiredUIAccess
    @Override
    public JComponent createComponent(Disposable parent) {
        final JPanel panel = mySettingsForm.getAdditionalSettingsPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel embedderVMOptionPanel = new JPanel(new BorderLayout());
        JLabel vmOptionLabel = new JLabel("VM options for importer:");
        embedderVMOptionPanel.add(vmOptionLabel, BorderLayout.WEST);
        vmOptionLabel.setLabelFor(myEmbedderVMOptions);

        embedderVMOptionPanel.add(myEmbedderVMOptions);
        panel.add(embedderVMOptionPanel);

        for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
            panel.add(additionalConfigurable.createComponent(parent));
        }
        return mySettingsForm.createComponent();
    }

    @Override
    @RequiredUIAccess
    public void disposeUIResources() {
        for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
            additionalConfigurable.disposeUIResources();
        }
    }

    @Override
    @RequiredUIAccess
    public boolean isModified() {
        for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
            if (additionalConfigurable.isModified()) {
                return true;
            }
        }

        if (!MavenServerManager.getInstance().getMavenEmbedderVMOptions().equals(myEmbedderVMOptions.getText())) {
            return true;
        }

        return mySettingsForm.isModified(myImportingSettings);
    }

    @Override
    @RequiredUIAccess
    public void apply() throws ConfigurationException {
        mySettingsForm.getData(myImportingSettings);

        MavenServerManager.getInstance().setMavenEmbedderVMOptions(myEmbedderVMOptions.getText());

        for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
            additionalConfigurable.apply();
        }
    }

    @Override
    @RequiredUIAccess
    public void reset() {
        mySettingsForm.setData(myImportingSettings);

        myEmbedderVMOptions.setText(MavenServerManager.getInstance().getMavenEmbedderVMOptions());

        for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
            additionalConfigurable.reset();
        }
    }

    @Override
    @Nls
    public String getDisplayName() {
        return ProjectBundle.message("maven.tab.importing");
    }

    @Override
    @Nullable
    public String getHelpTopic() {
        return "reference.settings.project.maven.importing";
    }

    @Override
    @Nonnull
    public String getId() {
        return getHelpTopic();
    }
}
