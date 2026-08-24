/* ==========================================================================
 * Copyright 2006 Mevenide Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */
package org.jetbrains.idea.maven.execution;

import com.intellij.java.language.projectRoots.JavaSdkType;
import consulo.disposer.Disposable;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.ui.awt.EnvironmentVariablesTextFieldWithBrowseButton;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.localize.LocalizeValue;
import consulo.module.ui.BundleBox;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.cmd.ParametersListUtil;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.TextBoxWithExpandAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import consulo.ui.util.TextWithMnemonic;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Maven runner settings, on the unified ui. Replaces the swing {@code GridBagLayout} based panel.
 */
public class MavenRunnerPanel {
    protected final Project myProject;
    private final boolean myRunConfigurationMode;

    /**
     * The ui is only built by {@link #createUIComponent(Disposable)} - the panel is instantiated from
     * configurable/settings-editor constructors, which are not necessarily on the ui thread.
     */
    private @Nullable CheckBox myRunInBackgroundCheckbox;
    private @Nullable TextBoxWithExpandAction myVMParametersEditor;
    private @Nullable EnvironmentVariablesTextFieldWithBrowseButton myEnvVariablesComponent;
    private @Nullable BundleBox myJdkCombo;
    private @Nullable CheckBox mySkipTestsCheckBox;
    private @Nullable MavenPropertiesTable myPropertiesTable;

    public MavenRunnerPanel(@Nonnull Project p, boolean isRunConfiguration) {
        myProject = p;
        myRunConfigurationMode = isRunConfiguration;
    }

    @RequiredUIAccess
    @Nonnull
    public Component createUIComponent(@Nonnull Disposable uiDisposable) {
        CheckBox runInBackgroundCheckbox = myRunInBackgroundCheckbox =
            CheckBox.create(MavenRunnerLocalize.mavenRunnerRunInBackground());

        TextBoxWithExpandAction vmParametersEditor = myVMParametersEditor = TextBoxWithExpandAction.create(
            PlatformIconGroup.actionsShow(),
            // the label carries a mnemonic, the expand dialog title must not
            TextWithMnemonic.parse(MavenRunnerLocalize.mavenRunnerVmOptions().get()).getText(),
            ParametersListUtil.DEFAULT_LINE_PARSER,
            ParametersListUtil.DEFAULT_LINE_JOINER
        );

        BundleBox jdkCombo = myJdkCombo = BundleBox.builder(ShowSettingsUtil.getInstance().getSdksModel(), uiDisposable)
            .withSdkTypeFilterByClass(JavaSdkType.class)
            .withNoneItem(LocalizeValue.localizeTODO("Auto Select"), PlatformIconGroup.actionsFind())
            .build();

        EnvironmentVariablesTextFieldWithBrowseButton envVariablesComponent = myEnvVariablesComponent =
            new EnvironmentVariablesTextFieldWithBrowseButton();
        envVariablesComponent.setPassParentEnvs(true);

        CheckBox skipTestsCheckBox = mySkipTestsCheckBox = CheckBox.create(MavenRunnerLocalize.mavenRunnerSkipTests());

        MavenPropertiesTable propertiesTable = myPropertiesTable = new MavenPropertiesTable(collectProperties());

        VerticalLayout top = VerticalLayout.create();
        if (!myRunConfigurationMode) {
            top.add(runInBackgroundCheckbox);
        }
        top.add(LabeledBuilder.filled(MavenRunnerLocalize.mavenRunnerVmOptions(), vmParametersEditor));
        top.add(DockLayout.create().left(LabeledBuilder.simple(MavenRunnerLocalize.mavenRunnerJre(), jdkCombo)));
        top.add(LabeledBuilder.filled(
            ExecutionLocalize.environmentVariablesComponentTitle(),
            envVariablesComponent.getComponent()
        ));

        DockLayout propertiesPanel = DockLayout.create();
        propertiesPanel.top(skipTestsCheckBox);
        propertiesPanel.center(propertiesTable.getComponent());

        DockLayout root = DockLayout.create();
        root.top(top);
        root.center(LabeledLayout.create(MavenRunnerLocalize.mavenRunnerProperties(), propertiesPanel));
        return root;
    }

    /**
     * @return {@code true} once {@link #createUIComponent(Disposable)} has run - settings can only be
     * read from, or pushed to, the ui after that.
     */
    protected final boolean isUiBuilt() {
        return myPropertiesTable != null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> collectProperties() {
        MavenProjectsManager s = MavenProjectsManager.getInstance(myProject);
        Map<String, String> result = new LinkedHashMap<>();

        for (MavenProject each : s.getProjects()) {
            Properties properties = each.getProperties();
            result.putAll((Map)properties);
        }

        return result;
    }

    @RequiredUIAccess
    protected void reset(MavenRunnerSettings data) {
        if (!isUiBuilt()) {
            return;
        }

        myRunInBackgroundCheckbox.setValue(data.isRunMavenInBackground());
        myVMParametersEditor.setValue(data.getVmOptions());
        mySkipTestsCheckBox.setValue(data.isSkipTests());

        myJdkCombo.setSelectedBundle(data.getJreName());
        myPropertiesTable.setDataFromMap(data.getMavenProperties());

        myEnvVariablesComponent.setEnvs(data.getEnvironmentProperties());
        myEnvVariablesComponent.setPassParentEnvs(data.isPassParentEnv());
    }

    @RequiredUIAccess
    protected void apply(MavenRunnerSettings data) {
        if (!isUiBuilt()) {
            return;
        }

        data.setRunMavenInBackground(myRunInBackgroundCheckbox.getValueOrError());
        data.setVmOptions(StringUtil.notNullize(myVMParametersEditor.getValue()).trim());
        data.setSkipTests(mySkipTestsCheckBox.getValueOrError());
        data.setJreName(myJdkCombo.getSelectedBundleName());

        data.setMavenProperties(myPropertiesTable.getDataAsMap());

        data.setEnvironmentProperties(myEnvVariablesComponent.getEnvs());
        data.setPassParentEnv(myEnvVariablesComponent.isPassParentEnvs());
    }

    public Project getProject() {
        return myProject;
    }
}
