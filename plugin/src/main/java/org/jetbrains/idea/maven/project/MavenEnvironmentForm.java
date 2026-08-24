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

import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.localize.LocalizeValue;
import consulo.maven.bundle.MavenBundleType;
import consulo.module.ui.BundleBox;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.TextBox;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Predicates;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Maven bundle / user settings file / local repository, on the unified ui. Replaces the swing
 * {@code .form} based panel.
 * <p>
 * The ui is built lazily by {@link #createComponent(Disposable)}; {@link #getData} / {@link #setData}
 * may be called before that, so the plain fields below stay the source of truth.
 */
public class MavenEnvironmentForm {
    private String myMavenBundleName = "";
    private String myUserSettingsFile = "";
    private String myLocalRepository = "";

    private @Nullable BundleBox myMavenBundleBox;
    private @Nullable PathOverrider myUserSettingsFileOverrider;
    private @Nullable PathOverrider myLocalRepositoryOverrider;

    private boolean myUpdating = false;
    private int myUpdateGeneration = 0;

    public MavenEnvironmentForm() {
    }

    @RequiredUIAccess
    @Nonnull
    public Component createComponent(Disposable uiDisposable) {
        BundleBox mavenBundleBox = myMavenBundleBox = new BundleBox(
            SdkTable.getInstance(),
            Predicates.equalTo(MavenBundleType.getInstance()),
            LocalizeValue.localizeTODO("Auto Select"),
            PlatformIconGroup.actionsFind()
        );

        FileChooserTextBoxBuilder.Controller settingsFileBox = FileChooserTextBoxBuilder.create(null)
            .uiDisposable(uiDisposable)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
            .dialogTitle(MavenProjectLocalize.mavenSelectMavenSettingsFile())
            .build();

        FileChooserTextBoxBuilder.Controller localRepositoryBox = FileChooserTextBoxBuilder.create(null)
            .uiDisposable(uiDisposable)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor())
            .dialogTitle(MavenProjectLocalize.mavenSelectLocalRepository())
            .build();

        CheckBox settingsOverrideCheckBox = CheckBox.create(MavenProjectLocalize.mavenEnvironmentOverride());
        CheckBox localRepositoryOverrideCheckBox = CheckBox.create(MavenProjectLocalize.mavenEnvironmentOverride());

        PathOverrider userSettingsFileOverrider = myUserSettingsFileOverrider = new PathOverrider(
            settingsFileBox,
            settingsOverrideCheckBox,
            this::scheduleDefaultsUpdate,
            () -> MavenUtil.resolveUserSettingsFile("")
        );

        PathOverrider localRepositoryOverrider = myLocalRepositoryOverrider = new PathOverrider(
            localRepositoryBox,
            localRepositoryOverrideCheckBox,
            this::scheduleDefaultsUpdate,
            () -> MavenUtil.resolveLocalRepository("", getMavenHome(), settingsFileBox.getValue())
        );

        // push the state gathered before the ui existed
        mavenBundleBox.setSelectedBundle(StringUtil.nullize(myMavenBundleName));
        userSettingsFileOverrider.reset(myUserSettingsFile);
        localRepositoryOverrider.reset(myLocalRepository);

        VerticalLayout root = VerticalLayout.create();
        root.add(LabeledBuilder.filled(MavenProjectLocalize.mavenEnvironmentBundle(), mavenBundleBox));
        root.add(overridableLine(
            MavenProjectLocalize.mavenEnvironmentUserSettingsFile(),
            settingsFileBox.getComponent(),
            settingsOverrideCheckBox
        ));
        root.add(overridableLine(
            MavenProjectLocalize.mavenEnvironmentLocalRepository(),
            localRepositoryBox.getComponent(),
            localRepositoryOverrideCheckBox
        ));
        return root;
    }

    @RequiredUIAccess
    private static Component overridableLine(LocalizeValue label, TextBox textBox, CheckBox overrideCheckBox) {
        DockLayout line = DockLayout.create();
        line.center(LabeledBuilder.filled(label, textBox));
        line.right(overrideCheckBox);
        return line;
    }

    /**
     * Recomputes the defaults of the non-overridden fields shortly after a text change, instead of on
     * every keystroke - resolving the local repository reads settings.xml.
     */
    @RequiredUIAccess
    private void scheduleDefaultsUpdate() {
        if (myUpdating) {
            return;
        }

        int generation = ++myUpdateGeneration;
        UIAccess uiAccess = UIAccess.current();

        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            () -> uiAccess.give(() -> {
                if (generation != myUpdateGeneration) {
                    return;
                }

                myUpdating = true;
                try {
                    PathOverrider userSettingsFileOverrider = myUserSettingsFileOverrider;
                    if (userSettingsFileOverrider != null) {
                        userSettingsFileOverrider.updateDefault();
                    }

                    PathOverrider localRepositoryOverrider = myLocalRepositoryOverrider;
                    if (localRepositoryOverrider != null) {
                        localRepositoryOverrider.updateDefault();
                    }
                }
                finally {
                    myUpdating = false;
                }
            }),
            100,
            TimeUnit.MILLISECONDS
        );
    }

    @RequiredUIAccess
    public boolean isModified(MavenGeneralSettings data) {
        MavenGeneralSettings formData = new MavenGeneralSettings();
        setData(formData);
        return !formData.equals(data);
    }

    /**
     * Form to settings.
     */
    @RequiredUIAccess
    public void setData(MavenGeneralSettings data) {
        BundleBox mavenBundleBox = myMavenBundleBox;
        if (mavenBundleBox != null) {
            myMavenBundleName = StringUtil.notNullize(mavenBundleBox.getSelectedBundleName());
        }

        PathOverrider userSettingsFileOverrider = myUserSettingsFileOverrider;
        if (userSettingsFileOverrider != null) {
            myUserSettingsFile = userSettingsFileOverrider.getResult();
        }

        PathOverrider localRepositoryOverrider = myLocalRepositoryOverrider;
        if (localRepositoryOverrider != null) {
            myLocalRepository = localRepositoryOverrider.getResult();
        }

        data.setMavenBundleName(myMavenBundleName);
        data.setUserSettingsFile(myUserSettingsFile);
        data.setLocalRepository(myLocalRepository);
    }

    /**
     * Settings to form.
     */
    @RequiredUIAccess
    public void getData(MavenGeneralSettings data) {
        myMavenBundleName = StringUtil.notNullize(data.getMavenBundleName());
        myUserSettingsFile = StringUtil.notNullize(data.getUserSettingsFile());
        myLocalRepository = StringUtil.notNullize(data.getLocalRepository());

        BundleBox mavenBundleBox = myMavenBundleBox;
        if (mavenBundleBox != null) {
            mavenBundleBox.setSelectedBundle(StringUtil.nullize(myMavenBundleName));
        }

        PathOverrider userSettingsFileOverrider = myUserSettingsFileOverrider;
        if (userSettingsFileOverrider != null) {
            userSettingsFileOverrider.reset(myUserSettingsFile);
        }

        PathOverrider localRepositoryOverrider = myLocalRepositoryOverrider;
        if (localRepositoryOverrider != null) {
            localRepositoryOverrider.reset(myLocalRepository);
        }
    }

    @Nonnull
    public String getMavenHome() {
        BundleBox mavenBundleBox = myMavenBundleBox;
        String bundleName = mavenBundleBox == null ? myMavenBundleName : mavenBundleBox.getSelectedBundleName();

        Sdk selectedSdk = StringUtil.isEmptyOrSpaces(bundleName) ? null : SdkTable.getInstance().findSdk(bundleName);
        if (selectedSdk == null) {
            File file = MavenUtil.resolveMavenHomeDirectory("");
            return file == null ? "" : file.getPath();
        }
        return StringUtil.notNullize(selectedSdk.getHomePath());
    }

    private interface PathProvider {
        default String getPath() {
            File file = getFile();
            return file == null ? "" : file.getPath();
        }

        @Nullable
        File getFile();
    }

    private static class PathOverrider {
        private final TextBox myTextBox;
        private final CheckBox myCheckBox;
        private final PathProvider myPathProvider;

        private Boolean myOverridden;
        private String myOverrideText;

        @RequiredUIAccess
        PathOverrider(
            FileChooserTextBoxBuilder.Controller controller,
            CheckBox checkBox,
            Runnable textChangeListener,
            PathProvider pathProvider
        ) {
            myTextBox = controller.getComponent();
            myCheckBox = checkBox;
            myPathProvider = pathProvider;

            myTextBox.addValueListener(e -> textChangeListener.run());
            myCheckBox.addValueListener(e -> update());
        }

        @RequiredUIAccess
        private void update() {
            boolean override = myCheckBox.getValueOrError();
            if (Comparing.equal(myOverridden, override)) {
                return;
            }

            myOverridden = override;

            myTextBox.setEditable(override);
            myTextBox.setEnabled(override && myCheckBox.isEnabled());

            if (override) {
                if (myOverrideText != null) {
                    myTextBox.setValue(myOverrideText);
                }
            }
            else {
                if (!StringUtil.isEmptyOrSpaces(myTextBox.getValue())) {
                    myOverrideText = myTextBox.getValue();
                }
                myTextBox.setValue(myPathProvider.getPath());
            }
        }

        @RequiredUIAccess
        private void updateDefault() {
            if (!myCheckBox.getValueOrError()) {
                myTextBox.setValue(myPathProvider.getPath());
            }
        }

        @RequiredUIAccess
        public void reset(String text) {
            myOverridden = null;
            myOverrideText = StringUtil.isEmptyOrSpaces(text) ? null : text;
            // don't fire - #update() below does the whole job at once
            myCheckBox.setValue(!StringUtil.isEmptyOrSpaces(text), false);
            update();
        }

        @RequiredUIAccess
        public String getResult() {
            return myCheckBox.getValueOrError() ? StringUtil.notNullize(myTextBox.getValue()).trim() : "";
        }
    }
}
