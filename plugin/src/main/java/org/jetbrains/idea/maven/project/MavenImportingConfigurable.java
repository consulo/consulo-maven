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
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.server.MavenServerManager;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class MavenImportingConfigurable implements SearchableConfigurable {
    private final MavenImportingSettings myImportingSettings;
    private final List<UnnamedConfigurable> myAdditionalConfigurables;

    /**
     * The ui is only built by {@link #createUIComponent(Disposable)} - this configurable is instantiated
     * from the {@code MavenSettings} service constructor, which is not on the ui thread.
     */
    private @Nullable MavenImportingSettingsForm mySettingsForm;
    private @Nullable TextBox myEmbedderVMOptions;

    public MavenImportingConfigurable(Project project) {
        myImportingSettings = MavenProjectsManager.getInstance(project).getImportingSettings();

        myAdditionalConfigurables = new ArrayList<>();
        for (final AdditionalMavenImportingSettings additionalSettings : AdditionalMavenImportingSettings.EP_NAME.getExtensionList()) {
            myAdditionalConfigurables.add(additionalSettings.createConfigurable(project));
        }
    }

    @RequiredUIAccess
    @Override
    public Component createUIComponent(@Nonnull Disposable parent) {
        MavenImportingSettingsForm settingsForm = mySettingsForm = new MavenImportingSettingsForm(false, false);
        TextBox embedderVMOptions = myEmbedderVMOptions = TextBox.create();

        VerticalLayout panel = settingsForm.getAdditionalSettingsPanel();
        panel.add(LabeledBuilder.filled(MavenProjectLocalize.mavenImportingEmbedderVmOptions(), embedderVMOptions));

        for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
            Component uiComponent = additionalConfigurable.createUIComponent(parent);
            if (uiComponent != null) {
                panel.add(uiComponent);
                continue;
            }

            // TODO extensions may still be swing-only
            JComponent swingComponent = additionalConfigurable.createComponent(parent);
            if (swingComponent != null) {
                panel.add(TargetAWT.wrap(swingComponent));
            }
        }
        return settingsForm.createComponent();
    }

    @Override
    @RequiredUIAccess
    public void disposeUIResources() {
        mySettingsForm = null;
        myEmbedderVMOptions = null;

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

        MavenImportingSettingsForm settingsForm = mySettingsForm;
        TextBox embedderVMOptions = myEmbedderVMOptions;
        if (settingsForm == null || embedderVMOptions == null) {
            return false;
        }

        return !MavenServerManager.getInstance().getMavenEmbedderVMOptions().equals(embedderVMOptions.getValueOrError())
            || settingsForm.isModified(myImportingSettings);
    }

    @Override
    @RequiredUIAccess
    public void apply() throws ConfigurationException {
        MavenImportingSettingsForm settingsForm = mySettingsForm;
        TextBox embedderVMOptions = myEmbedderVMOptions;
        if (settingsForm != null && embedderVMOptions != null) {
            settingsForm.getData(myImportingSettings);

            MavenServerManager.getInstance().setMavenEmbedderVMOptions(embedderVMOptions.getValueOrError());
        }

        for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
            additionalConfigurable.apply();
        }
    }

    @Override
    @RequiredUIAccess
    public void reset() {
        MavenImportingSettingsForm settingsForm = mySettingsForm;
        TextBox embedderVMOptions = myEmbedderVMOptions;
        if (settingsForm != null && embedderVMOptions != null) {
            settingsForm.setData(myImportingSettings);

            embedderVMOptions.setValue(MavenServerManager.getInstance().getMavenEmbedderVMOptions());
        }

        for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
            additionalConfigurable.reset();
        }
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return MavenProjectLocalize.mavenTabImporting();
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
