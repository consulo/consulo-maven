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
package org.jetbrains.idea.maven.project;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;

import consulo.ui.ex.awt.EnumComboBoxModel;
import consulo.ui.ex.awt.ListCellRendererWrapper;

public class MavenImportingSettingsForm {
    private JPanel myPanel;

    private JCheckBox mySearchRecursivelyCheckBox;

    private JCheckBox myImportAutomaticallyBox;
    private JCheckBox myCreateModulesForAggregators;
    private JCheckBox myCreateGroupsCheckBox;
    private JComboBox myUpdateFoldersOnImportPhaseComboBox;
    private JCheckBox myKeepSourceFoldersCheckBox;
    private JCheckBox myUseMavenOutputCheckBox;
    private JCheckBox myDownloadSourcesCheckBox;
    private JCheckBox myDownloadDocsCheckBox;

    private JPanel myAdditionalSettingsPanel;
    private JComboBox myGeneratedSourcesComboBox;
    private JCheckBox myExcludeTargetFolderCheckBox;

    public MavenImportingSettingsForm(boolean isImportStep, boolean isCreatingNewProject) {
        mySearchRecursivelyCheckBox.setVisible(isImportStep);

        myUpdateFoldersOnImportPhaseComboBox.setModel(new DefaultComboBoxModel(MavenImportingSettings.UPDATE_FOLDERS_PHASES));

        myGeneratedSourcesComboBox.setModel(new EnumComboBoxModel<>(MavenImportingSettings.GeneratedSourcesFolder.class));
        myGeneratedSourcesComboBox.setRenderer(new ListCellRendererWrapper() {
            @Override
            public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
                if (value instanceof MavenImportingSettings.GeneratedSourcesFolder) {
                    setText(((MavenImportingSettings.GeneratedSourcesFolder)value).title);
                }
            }
        });
    }

    public JComponent createComponent() {
        return myPanel;
    }

    public void getData(MavenImportingSettings data) {
        data.setLookForNested(mySearchRecursivelyCheckBox.isSelected());

        data.setImportAutomatically(myImportAutomaticallyBox.isSelected());
        data.setCreateModulesForAggregators(myCreateModulesForAggregators.isSelected());
        data.setCreateModuleGroups(myCreateGroupsCheckBox.isSelected());

        data.setKeepSourceFolders(myKeepSourceFoldersCheckBox.isSelected());
        data.setExcludeTargetFolder(myExcludeTargetFolderCheckBox.isSelected());
        data.setUseMavenOutput(myUseMavenOutputCheckBox.isSelected());

        data.setUpdateFoldersOnImportPhase((String)myUpdateFoldersOnImportPhaseComboBox.getSelectedItem());
        data.setGeneratedSourcesFolder((MavenImportingSettings.GeneratedSourcesFolder)myGeneratedSourcesComboBox.getSelectedItem());

        data.setDownloadSourcesAutomatically(myDownloadSourcesCheckBox.isSelected());
        data.setDownloadDocsAutomatically(myDownloadDocsCheckBox.isSelected());
    }

    public void setData(MavenImportingSettings data) {
        mySearchRecursivelyCheckBox.setSelected(data.isLookForNested());

        myImportAutomaticallyBox.setSelected(data.isImportAutomatically());
        myCreateModulesForAggregators.setSelected(data.isCreateModulesForAggregators());
        myCreateGroupsCheckBox.setSelected(data.isCreateModuleGroups());

        myKeepSourceFoldersCheckBox.setSelected(data.isKeepSourceFolders());
        myExcludeTargetFolderCheckBox.setSelected(data.isExcludeTargetFolder());
        myUseMavenOutputCheckBox.setSelected(data.isUseMavenOutput());

        myUpdateFoldersOnImportPhaseComboBox.setSelectedItem(data.getUpdateFoldersOnImportPhase());
        myGeneratedSourcesComboBox.setSelectedItem(data.getGeneratedSourcesFolder());

        myDownloadSourcesCheckBox.setSelected(data.isDownloadSourcesAutomatically());
        myDownloadDocsCheckBox.setSelected(data.isDownloadDocsAutomatically());
    }

    public boolean isModified(MavenImportingSettings settings) {
        MavenImportingSettings formData = new MavenImportingSettings();
        getData(formData);
        return !formData.equals(settings);
    }

    public JPanel getAdditionalSettingsPanel() {
        return myAdditionalSettingsPanel;
    }
}
