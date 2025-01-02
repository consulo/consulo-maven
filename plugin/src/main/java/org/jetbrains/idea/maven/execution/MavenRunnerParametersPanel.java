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
package org.jetbrains.idea.maven.execution;

import consulo.fileChooser.FileChooserDescriptor;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.editor.ui.awt.TextFieldCompletionProvider;
import consulo.maven.icon.MavenIconGroup;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.process.cmd.ParametersList;
import consulo.process.cmd.ParametersListUtil;
import consulo.project.Project;
import consulo.ui.TextBoxWithExtensions;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.FileChooserTextBoxBuilder;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.awt.FormBuilder;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.execution.cmd.ParametersListLexer;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenRunnerParametersPanel {
    private EditorTextField myGoalsEditor;
    @Nullable
    private EditorTextField myProfilesEditor;
    @Nullable
    private JCheckBox myResolveToWorkspaceCheckBox;

    private FileChooserTextBoxBuilder.Controller myWorkingDirectory;
    private FormBuilder myFormBuilder = FormBuilder.createFormBuilder();

    @RequiredUIAccess
    public MavenRunnerParametersPanel(@Nonnull final Project project) {
        this(project, true, true);
    }

    @RequiredUIAccess
    public MavenRunnerParametersPanel(@Nonnull final Project project, boolean withResolveLocal, boolean withProfiles) {
        FileChooserTextBoxBuilder workDirBuilder = FileChooserTextBoxBuilder.create(project);
        workDirBuilder.dialogTitle(MavenRunnerLocalize.mavenSelectMavenProjectFile());
        workDirBuilder.fileChooserDescriptor(new FileChooserDescriptor(false, true, false, false, false, false) {
            @Override
            @RequiredUIAccess
            public boolean isFileSelectable(VirtualFile file) {
                return super.isFileSelectable(file) && file.findChild(MavenConstants.POM_XML) != null;
            }
        });

        myWorkingDirectory = workDirBuilder.build();

        JComponent workTextField = (JComponent) TargetAWT.to(myWorkingDirectory.getComponent());
        if (workTextField instanceof JTextField jTextField) {
            // TODO [VISTALL] dirty hack with old UI form builder which change filling by cols option
            jTextField.setColumns(0);
        }
        myFormBuilder.addLabeledComponent("Working directory", workTextField);

        if (!project.isDefault()) {
            TextFieldCompletionProvider profilesCompletionProvider = new TextFieldCompletionProvider(true) {
                @Override
                public final void addCompletionVariants(
                    @Nonnull String text,
                    int offset,
                    @Nonnull String prefix,
                    @Nonnull CompletionResultSet result
                ) {
                    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
                    for (String profile : manager.getAvailableProfiles()) {
                        result.addElement(LookupElementBuilder.create(ParametersListUtil.join(profile)));
                    }
                }

                @Nonnull
                @Override
                public String getPrefix(@Nonnull String currentTextPrefix) {
                    ParametersListLexer lexer = new ParametersListLexer(currentTextPrefix);
                    while (lexer.nextToken()) {
                        if (lexer.getTokenEnd() == currentTextPrefix.length()) {
                            String prefix = lexer.getCurrentToken();
                            if (prefix.startsWith("-") || prefix.startsWith("!")) {
                                prefix = prefix.substring(1);
                            }
                            return prefix;
                        }
                    }

                    return "";
                }
            };

            myGoalsEditor = new MavenArgumentsCompletionProvider(project).createEditor(project);
            myFormBuilder.addLabeledComponent("Command line", myGoalsEditor);

            if (withProfiles) {
                myProfilesEditor = profilesCompletionProvider.createEditor(project);
                myFormBuilder.addLabeledComponent("Profiles (separated with space)", myProfilesEditor);
                JLabel label = new JBLabel("add prefix '-' to disable profile, e.g. '-test'");
                label.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
                label.setForeground(JBColor.GRAY);

                myFormBuilder.addComponentToRightColumn(label);
            }
        }

        if (withResolveLocal) {
            myResolveToWorkspaceCheckBox = new JBCheckBox("Resolve Workspace artifacts");
            myResolveToWorkspaceCheckBox.setToolTipText(
                "In case of multi-project workspace, dependencies will be looked for in the workspace first, " +
                    "and only after that in local repository."
            );

            myFormBuilder.addComponent(myResolveToWorkspaceCheckBox);
        }

        myWorkingDirectory.getComponent().addFirstExtension(new TextBoxWithExtensions.Extension(
            false,
            MavenIconGroup.mavenlogotransparent(),
            MavenIconGroup.mavenlogo(),
            clickEvent -> MavenSelectProjectPopup.buildPopup(
                    MavenProjectsManager.getInstance(project),
                    mavenProject -> myWorkingDirectory.setValue(mavenProject.getDirectory())
                )
                .show(new RelativePoint(MouseInfo.getPointerInfo().getLocation()))
        ));
    }

    @Nonnull
    public JComponent createComponent() {
        return myFormBuilder.getPanel();
    }

    public EditorTextField getGoalsEditor() {
        return myGoalsEditor;
    }

    public FileChooserTextBoxBuilder.Controller getWorkingDirectory() {
        return myWorkingDirectory;
    }

    public void disposeUIResources() {
    }

    public String getDisplayName() {
        return MavenRunnerLocalize.mavenRunnerParametersTitle().get();
    }

    @RequiredUIAccess
    protected void setData(final MavenRunnerParameters data) {
        data.setWorkingDirPath(myWorkingDirectory.getValue());
        data.setGoals(ParametersListUtil.parse(myGoalsEditor.getText()));
        if (myResolveToWorkspaceCheckBox != null) {
            data.setResolveToWorkspace(myResolveToWorkspaceCheckBox.isSelected());
        }

        if (myProfilesEditor != null) {
            Map<String, Boolean> profilesMap = new LinkedHashMap<>();

            List<String> profiles = ParametersListUtil.parse(myProfilesEditor.getText());

            for (String profile : profiles) {
                Boolean isEnabled = true;
                if (profile.startsWith("-") || profile.startsWith("!")) {
                    profile = profile.substring(1);
                    if (profile.isEmpty()) {
                        continue;
                    }

                    isEnabled = false;
                }

                profilesMap.put(profile, isEnabled);
            }
            data.setProfilesMap(profilesMap);
        }
    }

    @RequiredUIAccess
    protected void getData(final MavenRunnerParameters data) {
        myWorkingDirectory.setValue(data.getWorkingDirPath());
        myGoalsEditor.setText(ParametersList.join(data.getGoals()));

        if (myResolveToWorkspaceCheckBox != null) {
            myResolveToWorkspaceCheckBox.setSelected(data.isResolveToWorkspace());
        }

        if (myProfilesEditor != null) {
            ParametersList parametersList = new ParametersList();

            for (Map.Entry<String, Boolean> entry : data.getProfilesMap().entrySet()) {
                String profileName = entry.getKey();

                if (!entry.getValue()) {
                    profileName = '-' + profileName;
                }

                parametersList.add(profileName);
            }

            myProfilesEditor.setText(parametersList.getParametersString());
        }
    }
}
