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

import consulo.application.AllIcons;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.maven.bundle.MavenBundleType;
import consulo.module.ui.awt.SdkComboBox;
import consulo.ui.ex.awt.LabeledComponent;
import consulo.ui.ex.awt.PanelWithAnchor;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awt.util.Alarm;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Conditions;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;

public class MavenEnvironmentForm implements PanelWithAnchor {
    private JPanel panel;
    private LabeledComponent<TextFieldWithBrowseButton> settingsFileComponent;
    private LabeledComponent<TextFieldWithBrowseButton> localRepositoryComponent;
    private JCheckBox settingsOverrideCheckBox;
    private JCheckBox localRepositoryOverrideCheckBox;
    private JPanel myMavenBundlePanel;
    private JComponent anchor;

    private final PathOverrider userSettingsFileOverrider;
    private final PathOverrider localRepositoryOverrider;

    private boolean isUpdating = false;
    private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    private final LabeledComponent<JComponent> myMavenComboBoxLabeled;
    private final SdkComboBox myMavenBundleBox;

    public MavenEnvironmentForm() {
        myMavenBundleBox = new SdkComboBox(
            SdkTable.getInstance(),
            Conditions.equalTo(MavenBundleType.getInstance()),
            null,
            "Auto Select",
            AllIcons.Actions.FindPlain
        );

        DocumentAdapter listener = new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                UIUtil.invokeLaterIfNeeded(() -> {
                    if (isUpdating) {
                        return;
                    }
                    if (!panel.isShowing()) {
                        return;
                    }

                    myUpdateAlarm.cancelAllRequests();
                    myUpdateAlarm.addRequest(
                        () -> {
                            isUpdating = true;
                            userSettingsFileOverrider.updateDefault();
                            localRepositoryOverrider.updateDefault();
                            isUpdating = false;
                        },
                        100
                    );
                });
            }
        };

        myMavenComboBoxLabeled = LabeledComponent.create(myMavenBundleBox, "Maven Bundle");
        myMavenBundlePanel.add(myMavenComboBoxLabeled, BorderLayout.CENTER);

        userSettingsFileOverrider = new PathOverrider(
            settingsFileComponent,
            settingsOverrideCheckBox,
            listener,
            () -> MavenUtil.resolveUserSettingsFile("")
        );

        localRepositoryOverrider = new PathOverrider(
            localRepositoryComponent,
            localRepositoryOverrideCheckBox,
            listener,
            () -> MavenUtil.resolveLocalRepository(
                "",
                getMavenHome(),
                settingsFileComponent.getComponent().getText()
            )
        );

        setAnchor(myMavenComboBoxLabeled.getLabel());
    }

    public boolean isModified(MavenGeneralSettings data) {
        MavenGeneralSettings formData = new MavenGeneralSettings();
        setData(formData);
        return !formData.equals(data);
    }

    public void setData(MavenGeneralSettings data) {
        data.setMavenBundleName(StringUtil.notNullize(myMavenBundleBox.getSelectedSdkName()));
        data.setUserSettingsFile(userSettingsFileOverrider.getResult());
        data.setLocalRepository(localRepositoryOverrider.getResult());
    }

    public void getData(MavenGeneralSettings data) {
        String mavenHome = data.getMavenBundleName();
        myMavenBundleBox.setSelectedSdk(StringUtil.nullize(mavenHome));
        userSettingsFileOverrider.reset(data.getUserSettingsFile());
        localRepositoryOverrider.reset(data.getLocalRepository());
    }

    @Nonnull
    public String getMavenHome() {
        Sdk selectedSdk = myMavenBundleBox.getSelectedSdk();
        if (selectedSdk == null) {
            File file = MavenUtil.resolveMavenHomeDirectory("");
            return file == null ? "" : file.getPath();
        }
        return StringUtil.notNullize(selectedSdk.getHomePath());
    }

    public JComponent createComponent(Disposable uiDisposable) {
        settingsFileComponent.getComponent().addBrowseFolderListener(
            MavenProjectLocalize.mavenSelectMavenSettingsFile().get(),
            "",
            null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        );
        localRepositoryComponent.getComponent().addBrowseFolderListener(
            MavenProjectLocalize.mavenSelectLocalRepository().get(),
            "",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        );
        return panel;
    }

    @Override
    public JComponent getAnchor() {
        return anchor;
    }

    @Override
    public void setAnchor(JComponent anchor) {
        this.anchor = anchor;
        myMavenComboBoxLabeled.setAnchor(anchor);
        settingsFileComponent.setAnchor(anchor);
        localRepositoryComponent.setAnchor(anchor);
    }

    private static interface PathProvider {
        default String getPath() {
            final File file = getFile();
            return file == null ? "" : file.getPath();
        }

        @Nullable
        File getFile();
    }

    private static class PathOverrider {
        private final TextFieldWithBrowseButton component;
        private final JCheckBox checkBox;
        private final PathProvider pathProvider;

        private Boolean isOverridden;
        private String overrideText;

        public PathOverrider(
            final LabeledComponent<TextFieldWithBrowseButton> component,
            final JCheckBox checkBox,
            DocumentListener docListener,
            PathProvider pathProvider
        ) {
            this.component = component.getComponent();
            this.component.getTextField().getDocument().addDocumentListener(docListener);
            this.checkBox = checkBox;
            this.pathProvider = pathProvider;
            checkBox.addActionListener(e -> update());
        }

        private void update() {
            final boolean override = checkBox.isSelected();
            if (Comparing.equal(isOverridden, override)) {
                return;
            }

            isOverridden = override;

            component.setEditable(override);
            component.setEnabled(override && checkBox.isEnabled());

            if (override) {
                if (overrideText != null) {
                    component.setText(overrideText);
                }
            }
            else {
                if (!StringUtil.isEmptyOrSpaces(component.getText())) {
                    overrideText = component.getText();
                }
                component.setText(pathProvider.getPath());
            }
        }

        private void updateDefault() {
            if (!checkBox.isSelected()) {
                component.setText(pathProvider.getPath());
            }
        }

        public void reset(String text) {
            isOverridden = null;
            checkBox.setSelected(!StringUtil.isEmptyOrSpaces(text));
            overrideText = StringUtil.isEmptyOrSpaces(text) ? null : text;
            update();
        }

        public String getResult() {
            return checkBox.isSelected() ? component.getText().trim() : "";
        }
    }
}
