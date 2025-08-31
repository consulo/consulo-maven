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
package org.jetbrains.idea.maven.wizards;

import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.localize.LocalizeValue;
import consulo.maven.importProvider.MavenImportModuleContext;
import consulo.ui.Button;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.wizard.WizardStep;
import consulo.ui.layout.DockLayout;
import consulo.ui.util.LabeledBuilder;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.project.MavenEnvironmentForm;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenImportingSettingsForm;

import javax.swing.*;
import java.awt.*;

public class MavenProjectImportStep implements WizardStep<MavenImportModuleContext> {
    private final JPanel myPanel;
    private FileChooserTextBoxBuilder.Controller myRootPathController;
    private final MavenImportingSettingsForm myImportingSettingsForm;
    private final MavenImportModuleContext myContext;

    private String myRootPath;

    @RequiredUIAccess
    public MavenProjectImportStep(MavenImportModuleContext context) {
        myContext = context;

        myImportingSettingsForm = new MavenImportingSettingsForm(true, context.isNewProject());

        myPanel = new JPanel(new BorderLayout());
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    public Component getComponent(@Nonnull MavenImportModuleContext context, @Nonnull Disposable disposable) {
        throw new UnsupportedOperationException("destop only");
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    public JComponent getSwingComponent(@Nonnull MavenImportModuleContext context, @Nonnull Disposable disposable) {
        myRootPathController = FileChooserTextBoxBuilder.create(context.getProject())
            .uiDisposable(disposable)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor())
            .dialogTitle(MavenProjectLocalize.mavenImportTitleSelectRoot())
            .build();

        if (myRootPath != null) {
            myRootPathController.setValue(myRootPath);
        }

        myPanel.add(TargetAWT.to(LabeledBuilder.filled(MavenProjectLocalize.mavenImportLabelSelectRoot(), myRootPathController.getComponent())), BorderLayout.NORTH);

        myPanel.add(myImportingSettingsForm.createComponent(), BorderLayout.CENTER);

        Button envSettingsButton = Button.create(MavenProjectLocalize.mavenImportEnvironmentSettings(), e -> {
            ShowSettingsUtil.getInstance().editConfigurable(myPanel, new MavenEnvironmentConfigurable());
        });

        DockLayout bottom = DockLayout.create();
        bottom.right(envSettingsButton);

        myPanel.add(TargetAWT.to(bottom), BorderLayout.SOUTH);

        return myPanel;
    }

    @Override
    @RequiredUIAccess
    public JComponent getSwingPreferredFocusedComponent() {
        return (JComponent) TargetAWT.to(myRootPathController.getComponent());
    }

    @Override
    @RequiredUIAccess
    public void onStepEnter(@Nonnull MavenImportModuleContext mavenImportModuleContext) {
        final VirtualFile rootDirectory = myContext.getRootDirectory();

        String path;
        if (rootDirectory != null) {
            path = rootDirectory.getPath();
        }
        else {
            path = myContext.getPath();
        }

        path = FileUtil.toSystemDependentName(path);
        if (myRootPathController != null) {
            myRootPathController.setValue(path);
        } else {
            myRootPath = path;
        }

        myImportingSettingsForm.setData(getImportingSettings());
    }

    @Override
    @RequiredUIAccess
    public void onStepLeave(@Nonnull MavenImportModuleContext mavenImportModuleContext) {
        MavenImportingSettings settings = getImportingSettings();
        myImportingSettingsForm.getData(settings);
        suggestProjectNameAndPath(settings.getDedicatedModuleDir(), myRootPathController.getValue());
        myContext.setRootDirectory(myContext.getProject(), myRootPathController.getValue());
    }

    protected void suggestProjectNameAndPath(final String alternativePath, final String path) {
        myContext.setPath(alternativePath != null && alternativePath.length() > 0 ? alternativePath : path);
        final String global = FileUtil.toSystemIndependentName(path);
        myContext.setName(global.substring(global.lastIndexOf("/") + 1));
    }

    private MavenGeneralSettings getGeneralSettings() {
        return myContext.getGeneralSettings();
    }

    private MavenImportingSettings getImportingSettings() {
        return myContext.getImportingSettings();
    }

    class MavenEnvironmentConfigurable implements Configurable {
        MavenEnvironmentForm myForm = new MavenEnvironmentForm();

        @Nonnull
        @Override
        public LocalizeValue getDisplayName() {
            return MavenProjectLocalize.mavenImportEnvironmentSettingsTitle();
        }

        @RequiredUIAccess
        @Override
        public JComponent createComponent(@Nonnull Disposable uiDisposable) {
            return myForm.createComponent(uiDisposable);
        }

        @RequiredUIAccess
        @Override
        public boolean isModified() {
            return myForm.isModified(getGeneralSettings());
        }

        @RequiredUIAccess
        @Override
        public void apply() throws ConfigurationException {
            myForm.setData(getGeneralSettings());
        }

        @RequiredUIAccess
        @Override
        public void reset() {
            myForm.getData(getGeneralSettings());
        }
    }
}
