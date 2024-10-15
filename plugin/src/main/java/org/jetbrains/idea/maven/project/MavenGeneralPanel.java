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

package org.jetbrains.idea.maven.project;

import consulo.disposer.Disposable;
import consulo.ui.ex.awt.CollectionComboBoxModel;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.PanelWithAnchor;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Ralf Quebbemann (ralfq@codehaus.org)
 */
public class MavenGeneralPanel implements PanelWithAnchor {
    private JCheckBox checkboxWorkOffline;
    private JPanel panel;
    private JComboBox outputLevelCombo;
    private JCheckBox checkboxProduceExceptionErrorMessages;
    private JComboBox checksumPolicyCombo;
    private JComboBox failPolicyCombo;
    private JComboBox pluginUpdatePolicyCombo;
    private JCheckBox checkboxUsePluginRegistry;
    private JCheckBox checkboxRecursive;
    private MavenEnvironmentForm mavenPathsForm;
    private JBLabel myMultiProjectBuildFailPolicyLabel;
    private JCheckBox alwaysUpdateSnapshotsCheckBox;
    private JTextField threadsEditor;
    private JComboBox<MaveOverrideCompilerPolicy> myOverrideBuiltInCompilerBox;
    private final DefaultComboBoxModel outputLevelComboModel = new DefaultComboBoxModel();
    private final DefaultComboBoxModel checksumPolicyComboModel = new DefaultComboBoxModel();
    private final DefaultComboBoxModel failPolicyComboModel = new DefaultComboBoxModel();
    private final DefaultComboBoxModel pluginUpdatePolicyComboModel = new DefaultComboBoxModel();
    private JComponent anchor;

    public MavenGeneralPanel() {
        myOverrideBuiltInCompilerBox.setModel(new CollectionComboBoxModel<>(List.of(MaveOverrideCompilerPolicy.values())));
        myOverrideBuiltInCompilerBox.setVisible(false);

        fillOutputLevelCombobox();
        fillChecksumPolicyCombobox();
        fillFailureBehaviorCombobox();
        fillPluginUpdatePolicyCombobox();

        setAnchor(myMultiProjectBuildFailPolicyLabel);
    }

    public void showOverrideCompilerBox() {
        myOverrideBuiltInCompilerBox.setVisible(true);
    }

    private void fillOutputLevelCombobox() {
        ComboBoxUtil.setModel(
            outputLevelCombo,
            outputLevelComboModel,
            Arrays.asList(MavenExecutionOptions.LoggingLevel.values()),
            each -> Pair.create(each.getDisplayString(), each)
        );
    }

    private void fillFailureBehaviorCombobox() {
        ComboBoxUtil.setModel(
            failPolicyCombo,
            failPolicyComboModel,
            Arrays.asList(MavenExecutionOptions.FailureMode.values()),
            each -> Pair.create(each.getDisplayString(), each)
        );
    }

    private void fillChecksumPolicyCombobox() {
        ComboBoxUtil.setModel(
            checksumPolicyCombo,
            checksumPolicyComboModel,
            Arrays.asList(MavenExecutionOptions.ChecksumPolicy.values()),
            each -> Pair.create(each.getDisplayString(), each)
        );
    }

    private void fillPluginUpdatePolicyCombobox() {
        ComboBoxUtil.setModel(
            pluginUpdatePolicyCombo,
            pluginUpdatePolicyComboModel,
            Arrays.asList(MavenExecutionOptions.PluginUpdatePolicy.values()),
            each -> Pair.create(each.getDisplayString(), each)
        );
    }

    public JComponent createComponent(@Nonnull Disposable uiDisposable) {
        mavenPathsForm.createComponent(uiDisposable); // have to initialize all listeners
        return panel;
    }

    protected void setData(MavenGeneralSettings data) {
        data.beginUpdate();

        data.setWorkOffline(checkboxWorkOffline.isSelected());
        mavenPathsForm.setData(data);

        data.setPrintErrorStackTraces(checkboxProduceExceptionErrorMessages.isSelected());
        data.setUsePluginRegistry(checkboxUsePluginRegistry.isSelected());
        data.setNonRecursive(!checkboxRecursive.isSelected());

        data.setOutputLevel((MavenExecutionOptions.LoggingLevel)ComboBoxUtil.getSelectedValue(outputLevelComboModel));
        data.setChecksumPolicy((MavenExecutionOptions.ChecksumPolicy)ComboBoxUtil.getSelectedValue(checksumPolicyComboModel));
        data.setFailureBehavior((MavenExecutionOptions.FailureMode)ComboBoxUtil.getSelectedValue(failPolicyComboModel));
        data.setPluginUpdatePolicy((MavenExecutionOptions.PluginUpdatePolicy)ComboBoxUtil.getSelectedValue(pluginUpdatePolicyComboModel));
        data.setAlwaysUpdateSnapshots(alwaysUpdateSnapshotsCheckBox.isSelected());
        data.setThreads(threadsEditor.getText());
        data.setOverrideCompilePolicy((MaveOverrideCompilerPolicy)myOverrideBuiltInCompilerBox.getSelectedItem());

        data.endUpdate();
    }

    protected void getData(MavenGeneralSettings data) {
        checkboxWorkOffline.setSelected(data.isWorkOffline());

        mavenPathsForm.getData(data);

        checkboxProduceExceptionErrorMessages.setSelected(data.isPrintErrorStackTraces());
        checkboxUsePluginRegistry.setSelected(data.isUsePluginRegistry());
        checkboxRecursive.setSelected(!data.isNonRecursive());
        alwaysUpdateSnapshotsCheckBox.setSelected(data.isAlwaysUpdateSnapshots());
        threadsEditor.setText(StringUtil.notNullize(data.getThreads()));
        myOverrideBuiltInCompilerBox.setSelectedItem(data.getOverrideCompilePolicy());

        ComboBoxUtil.select(outputLevelComboModel, data.getOutputLevel());
        ComboBoxUtil.select(checksumPolicyComboModel, data.getChecksumPolicy());
        ComboBoxUtil.select(failPolicyComboModel, data.getFailureBehavior());
        ComboBoxUtil.select(pluginUpdatePolicyComboModel, data.getPluginUpdatePolicy());
    }

    @Nls
    public String getDisplayName() {
        return MavenProjectLocalize.mavenTabGeneral().get();
    }

    @Override
    public JComponent getAnchor() {
        return anchor;
    }

    @Override
    public void setAnchor(JComponent anchor) {
        this.anchor = anchor;
        myMultiProjectBuildFailPolicyLabel.setAnchor(anchor);
        mavenPathsForm.setAnchor(anchor);
    }
}
