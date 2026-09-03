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

import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.HtmlLabel;
import consulo.ui.Label;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;

import java.util.List;

/**
 * Maven importing settings, on the unified ui. Shared between the import wizard step and the
 * {@code Settings | Build, Execution, Deployment | Maven | Importing} configurable.
 */
public class MavenImportingSettingsForm {
    private final CheckBox mySearchRecursivelyCheckBox;

    private final CheckBox myImportAutomaticallyBox;
    private final CheckBox myCreateModulesForAggregators;
    private final CheckBox myCreateGroupsCheckBox;
    private final CheckBox myKeepSourceFoldersCheckBox;
    private final CheckBox myExcludeTargetFolderCheckBox;
    private final CheckBox myUseMavenOutputCheckBox;

    private final ComboBox<String> myUpdateFoldersOnImportPhaseComboBox;
    private final ComboBox<MavenImportingSettings.GeneratedSourcesFolder> myGeneratedSourcesComboBox;

    private final CheckBox myDownloadSourcesCheckBox;
    private final CheckBox myDownloadDocsCheckBox;

    /**
     * Extension point for {@link AdditionalMavenImportingSettings} - filled by the configurable, empty in the wizard.
     */
    private final VerticalLayout myAdditionalSettingsPanel = VerticalLayout.create();

    private final Component myComponent;

    @RequiredUIAccess
    public MavenImportingSettingsForm(boolean isImportStep, boolean isCreatingNewProject) {
        mySearchRecursivelyCheckBox = CheckBox.create(MavenProjectLocalize.mavenImportingSearchRecursively());
        mySearchRecursivelyCheckBox.setVisible(isImportStep);

        myImportAutomaticallyBox = CheckBox.create(MavenProjectLocalize.mavenImportingImportAutomatically());
        myImportAutomaticallyBox.setToolTipText(MavenProjectLocalize.mavenImportingImportAutomaticallyTooltip());

        myCreateModulesForAggregators = CheckBox.create(MavenProjectLocalize.mavenImportingCreateModulesForAggregators());
        myCreateGroupsCheckBox = CheckBox.create(MavenProjectLocalize.mavenImportingCreateGroups());
        myKeepSourceFoldersCheckBox = CheckBox.create(MavenProjectLocalize.mavenImportingKeepSourceFolders());

        myExcludeTargetFolderCheckBox = CheckBox.create(MavenProjectLocalize.mavenImportingExcludeTargetFolder());
        myExcludeTargetFolderCheckBox.setToolTipText(MavenProjectLocalize.mavenImportingExcludeTargetFolderTooltip());

        myUseMavenOutputCheckBox = CheckBox.create(MavenProjectLocalize.mavenImportingUseMavenOutput());
        myUseMavenOutputCheckBox.setToolTipText(MavenProjectLocalize.mavenImportingUseMavenOutputTooltip());

        myGeneratedSourcesComboBox = ComboBox.create(MavenImportingSettings.GeneratedSourcesFolder.values());
        myGeneratedSourcesComboBox.setTextRenderer(
            folder -> folder == null ? LocalizeValue.empty() : LocalizeValue.of(folder.title)
        );

        myUpdateFoldersOnImportPhaseComboBox = ComboBox.create(List.of(MavenImportingSettings.UPDATE_FOLDERS_PHASES));

        myDownloadSourcesCheckBox = CheckBox.create(MavenProjectLocalize.mavenImportingDownloadSources());
        myDownloadDocsCheckBox = CheckBox.create(MavenProjectLocalize.mavenImportingDownloadDocs());

        VerticalLayout root = VerticalLayout.create();
        root.add(mySearchRecursivelyCheckBox);
        root.add(myImportAutomaticallyBox);
        root.add(myCreateModulesForAggregators);
        root.add(myCreateGroupsCheckBox);
        root.add(myKeepSourceFoldersCheckBox);
        root.add(myExcludeTargetFolderCheckBox);
        root.add(myUseMavenOutputCheckBox);

        root.add(DockLayout.create().left(LabeledBuilder.simple(
            MavenProjectLocalize.mavenImportingGeneratedSourcesFolder(),
            myGeneratedSourcesComboBox
        )));

        root.add(DockLayout.create().left(LabeledBuilder.simple(
            MavenProjectLocalize.mavenImportingUpdateFoldersPhase(),
            myUpdateFoldersOnImportPhaseComboBox
        )));

        root.add(HtmlLabel.create(MavenProjectLocalize.mavenImportingUpdateFoldersNote()));

        HorizontalLayout downloadLine = HorizontalLayout.create();
        downloadLine.add(Label.create(MavenProjectLocalize.mavenImportingAutomaticallyDownload()));
        downloadLine.add(myDownloadSourcesCheckBox);
        downloadLine.add(myDownloadDocsCheckBox);
        root.add(DockLayout.create().left(downloadLine));

        root.add(myAdditionalSettingsPanel);

        myComponent = root;
    }

    @Nonnull
    public Component createComponent() {
        return myComponent;
    }

    @RequiredUIAccess
    public void getData(MavenImportingSettings data) {
        data.setLookForNested(mySearchRecursivelyCheckBox.getValueOrError());

        data.setImportAutomatically(myImportAutomaticallyBox.getValueOrError());
        data.setCreateModulesForAggregators(myCreateModulesForAggregators.getValueOrError());
        data.setCreateModuleGroups(myCreateGroupsCheckBox.getValueOrError());

        data.setKeepSourceFolders(myKeepSourceFoldersCheckBox.getValueOrError());
        data.setExcludeTargetFolder(myExcludeTargetFolderCheckBox.getValueOrError());
        data.setUseMavenOutput(myUseMavenOutputCheckBox.getValueOrError());

        data.setUpdateFoldersOnImportPhase(myUpdateFoldersOnImportPhaseComboBox.getValue());
        data.setGeneratedSourcesFolder(myGeneratedSourcesComboBox.getValue());

        data.setDownloadSourcesAutomatically(myDownloadSourcesCheckBox.getValueOrError());
        data.setDownloadDocsAutomatically(myDownloadDocsCheckBox.getValueOrError());
    }

    @RequiredUIAccess
    public void setData(MavenImportingSettings data) {
        mySearchRecursivelyCheckBox.setValue(data.isLookForNested());

        myImportAutomaticallyBox.setValue(data.isImportAutomatically());
        myCreateModulesForAggregators.setValue(data.isCreateModulesForAggregators());
        myCreateGroupsCheckBox.setValue(data.isCreateModuleGroups());

        myKeepSourceFoldersCheckBox.setValue(data.isKeepSourceFolders());
        myExcludeTargetFolderCheckBox.setValue(data.isExcludeTargetFolder());
        myUseMavenOutputCheckBox.setValue(data.isUseMavenOutput());

        myUpdateFoldersOnImportPhaseComboBox.setValue(data.getUpdateFoldersOnImportPhase());
        myGeneratedSourcesComboBox.setValue(data.getGeneratedSourcesFolder());

        myDownloadSourcesCheckBox.setValue(data.isDownloadSourcesAutomatically());
        myDownloadDocsCheckBox.setValue(data.isDownloadDocsAutomatically());
    }

    @RequiredUIAccess
    public boolean isModified(MavenImportingSettings settings) {
        MavenImportingSettings formData = new MavenImportingSettings();
        getData(formData);
        return !formData.equals(settings);
    }

    @Nonnull
    public VerticalLayout getAdditionalSettingsPanel() {
        return myAdditionalSettingsPanel;
    }
}
