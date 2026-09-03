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
import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.HtmlLabel;
import consulo.ui.Label;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;

import java.util.function.Function;

/**
 * General maven settings, on the unified ui. Replaces the swing {@code .form} based panel.
 *
 * @author Ralf Quebbemann (ralfq@codehaus.org)
 */
public class MavenGeneralPanel {
    private final CheckBox myWorkOfflineCheckBox;
    private final CheckBox myUsePluginRegistryCheckBox;
    private final CheckBox myRecursiveCheckBox;
    private final CheckBox myProduceExceptionErrorMessagesCheckBox;
    private final CheckBox myAlwaysUpdateSnapshotsCheckBox;

    private final ComboBox<MaveOverrideCompilerPolicy> myOverrideBuiltInCompilerBox;
    private final ComboBox<MavenExecutionOptions.LoggingLevel> myOutputLevelCombo;
    private final ComboBox<MavenExecutionOptions.ChecksumPolicy> myChecksumPolicyCombo;
    private final ComboBox<MavenExecutionOptions.FailureMode> myFailPolicyCombo;
    private final ComboBox<MavenExecutionOptions.PluginUpdatePolicy> myPluginUpdatePolicyCombo;

    private final TextBox myThreadsBox;

    private final MavenEnvironmentForm myMavenPathsForm = new MavenEnvironmentForm();

    private final Component myOverrideCompilerLine;

    @RequiredUIAccess
    public MavenGeneralPanel() {
        myWorkOfflineCheckBox = CheckBox.create(MavenProjectLocalize.mavenGeneralWorkOffline());
        myWorkOfflineCheckBox.setToolTipText(MavenProjectLocalize.mavenGeneralWorkOfflineTooltip());

        myUsePluginRegistryCheckBox = CheckBox.create(MavenProjectLocalize.mavenGeneralUsePluginRegistry());
        myUsePluginRegistryCheckBox.setToolTipText(MavenProjectLocalize.mavenGeneralUsePluginRegistryTooltip());

        myRecursiveCheckBox = CheckBox.create(MavenProjectLocalize.mavenGeneralExecuteGoalsRecursively());
        myRecursiveCheckBox.setToolTipText(MavenProjectLocalize.mavenGeneralExecuteGoalsRecursivelyTooltip());

        myProduceExceptionErrorMessagesCheckBox = CheckBox.create(MavenProjectLocalize.mavenGeneralPrintExceptionStackTraces());
        myProduceExceptionErrorMessagesCheckBox.setToolTipText(MavenProjectLocalize.mavenGeneralPrintExceptionStackTracesTooltip());

        myAlwaysUpdateSnapshotsCheckBox = CheckBox.create(MavenProjectLocalize.mavenGeneralAlwaysUpdateSnapshots());
        myAlwaysUpdateSnapshotsCheckBox.setToolTipText(MavenProjectLocalize.mavenGeneralAlwaysUpdateSnapshotsTooltip());

        myOverrideBuiltInCompilerBox = ComboBox.create(MaveOverrideCompilerPolicy.values());
        myOverrideBuiltInCompilerBox.setTextRenderer(
            policy -> policy == null ? LocalizeValue.empty() : LocalizeValue.of(policy.name())
        );

        myOutputLevelCombo = displayStringCombo(
            MavenExecutionOptions.LoggingLevel.values(),
            MavenExecutionOptions.LoggingLevel::getDisplayString
        );
        myChecksumPolicyCombo = displayStringCombo(
            MavenExecutionOptions.ChecksumPolicy.values(),
            MavenExecutionOptions.ChecksumPolicy::getDisplayString
        );
        myFailPolicyCombo = displayStringCombo(
            MavenExecutionOptions.FailureMode.values(),
            MavenExecutionOptions.FailureMode::getDisplayString
        );
        myPluginUpdatePolicyCombo = displayStringCombo(
            MavenExecutionOptions.PluginUpdatePolicy.values(),
            MavenExecutionOptions.PluginUpdatePolicy::getDisplayString
        );

        myThreadsBox = TextBox.create();
        myThreadsBox.setToolTipText(MavenProjectLocalize.mavenGeneralThreadsTooltip());

        myOverrideCompilerLine = DockLayout.create().left(LabeledBuilder.simple(
            MavenProjectLocalize.mavenGeneralOverrideCompilerPolicy(),
            myOverrideBuiltInCompilerBox
        ));
        myOverrideCompilerLine.setVisible(false);
    }

    @RequiredUIAccess
    private static <E> ComboBox<E> displayStringCombo(E[] values, Function<E, String> displayString) {
        ComboBox<E> comboBox = ComboBox.create(values);
        comboBox.setTextRenderer(value -> value == null ? LocalizeValue.empty() : LocalizeValue.of(displayString.apply(value)));
        return comboBox;
    }

    @RequiredUIAccess
    public void showOverrideCompilerBox() {
        myOverrideCompilerLine.setVisible(true);
    }

    @RequiredUIAccess
    @Nonnull
    public Component createComponent(@Nonnull Disposable uiDisposable) {
        VerticalLayout root = VerticalLayout.create();
        root.add(myWorkOfflineCheckBox);
        root.add(myUsePluginRegistryCheckBox);
        root.add(myRecursiveCheckBox);
        root.add(myProduceExceptionErrorMessagesCheckBox);
        root.add(myAlwaysUpdateSnapshotsCheckBox);

        root.add(myOverrideCompilerLine);

        root.add(DockLayout.create().left(LabeledBuilder.simple(
            MavenProjectLocalize.mavenGeneralOutputLevel(),
            myOutputLevelCombo
        )));
        root.add(DockLayout.create().left(LabeledBuilder.simple(
            MavenProjectLocalize.mavenGeneralChecksumPolicy(),
            myChecksumPolicyCombo
        )));
        root.add(DockLayout.create().left(LabeledBuilder.simple(
            MavenProjectLocalize.mavenGeneralFailPolicy(),
            myFailPolicyCombo
        )));

        HorizontalLayout pluginUpdateLine = HorizontalLayout.create();
        pluginUpdateLine.add(LabeledBuilder.simple(MavenProjectLocalize.mavenGeneralPluginUpdatePolicy(), myPluginUpdatePolicyCombo));
        pluginUpdateLine.add(Label.create(MavenProjectLocalize.mavenGeneralPluginUpdatePolicyNote()));
        root.add(DockLayout.create().left(pluginUpdateLine));

        HorizontalLayout threadsLine = HorizontalLayout.create();
        threadsLine.add(HtmlLabel.create(MavenProjectLocalize.mavenGeneralThreads()));
        threadsLine.add(myThreadsBox);
        root.add(DockLayout.create().left(threadsLine));

        root.add(myMavenPathsForm.createComponent(uiDisposable));
        return root;
    }

    /**
     * Panel to settings.
     */
    @RequiredUIAccess
    protected void setData(MavenGeneralSettings data) {
        data.beginUpdate();

        data.setWorkOffline(myWorkOfflineCheckBox.getValueOrError());
        myMavenPathsForm.setData(data);

        data.setPrintErrorStackTraces(myProduceExceptionErrorMessagesCheckBox.getValueOrError());
        data.setUsePluginRegistry(myUsePluginRegistryCheckBox.getValueOrError());
        data.setNonRecursive(!myRecursiveCheckBox.getValueOrError());

        data.setOutputLevel(myOutputLevelCombo.getValue());
        data.setChecksumPolicy(myChecksumPolicyCombo.getValue());
        data.setFailureBehavior(myFailPolicyCombo.getValue());
        data.setPluginUpdatePolicy(myPluginUpdatePolicyCombo.getValue());
        data.setAlwaysUpdateSnapshots(myAlwaysUpdateSnapshotsCheckBox.getValueOrError());
        data.setThreads(StringUtil.notNullize(myThreadsBox.getValue()));
        data.setOverrideCompilePolicy(myOverrideBuiltInCompilerBox.getValue());

        data.endUpdate();
    }

    /**
     * Settings to panel.
     */
    @RequiredUIAccess
    protected void getData(MavenGeneralSettings data) {
        myWorkOfflineCheckBox.setValue(data.isWorkOffline());

        myMavenPathsForm.getData(data);

        myProduceExceptionErrorMessagesCheckBox.setValue(data.isPrintErrorStackTraces());
        myUsePluginRegistryCheckBox.setValue(data.isUsePluginRegistry());
        myRecursiveCheckBox.setValue(!data.isNonRecursive());
        myAlwaysUpdateSnapshotsCheckBox.setValue(data.isAlwaysUpdateSnapshots());
        myThreadsBox.setValue(StringUtil.notNullize(data.getThreads()));
        myOverrideBuiltInCompilerBox.setValue(data.getOverrideCompilePolicy());

        myOutputLevelCombo.setValue(data.getOutputLevel());
        myChecksumPolicyCombo.setValue(data.getChecksumPolicy());
        myFailPolicyCombo.setValue(data.getFailureBehavior());
        myPluginUpdatePolicyCombo.setValue(data.getPluginUpdatePolicy());
    }

    @Nonnull
    public LocalizeValue getDisplayName() {
        return MavenProjectLocalize.mavenTabGeneral();
    }
}
