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
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.ui.EditorBox;
import consulo.language.editor.ui.EditorBoxBuilder;
import consulo.language.editor.ui.EditorBoxBuilderFactory;
import consulo.language.editor.ui.awt.TextFieldCompletionProvider;
import consulo.localize.LocalizeValue;
import consulo.maven.icon.MavenIconGroup;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.process.cmd.ParametersList;
import consulo.process.cmd.ParametersListUtil;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.maven.execution.cmd.ParametersListLexer;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenRunnerParametersPanel {
    private final FileChooserTextBoxBuilder.Controller myWorkingDirectory;
    private final EditorBox myGoalsEditor;
    private final @Nullable EditorBox myProfilesEditor;
    private final @Nullable CheckBox myResolveToWorkspaceCheckBox;

    private final Component myComponent;

    @RequiredUIAccess
    public MavenRunnerParametersPanel(Project project) {
        this(project, true, true);
    }

    @RequiredUIAccess
    public MavenRunnerParametersPanel(Project project, boolean withResolveLocal, boolean withProfiles) {
        FormBuilder formBuilder = FormBuilder.create();

        FileChooserTextBoxBuilder workDirBuilder = FileChooserTextBoxBuilder.create(project);
        workDirBuilder.dialogTitle(MavenRunnerLocalize.mavenSelectMavenProjectFile());
        workDirBuilder.fileChooserDescriptor(new FileChooserDescriptor(false, true, false, false, false, false) {
            @Override
            @RequiredUIAccess
            public boolean isFileSelectable(VirtualFile file) {
                return super.isFileSelectable(file) && file.findChild(MavenConstants.POM_XML) != null;
            }
        });

        workDirBuilder.firstActions(new DumbAwareAction(
            MavenRunnerLocalize.mavenRunnerSelectMavenModule(),
            LocalizeValue.empty(),
            MavenIconGroup.mavenlogo()
        ) {
            @RequiredUIAccess
            @Override
            public void actionPerformed(AnActionEvent e) {
                MavenProjectsManager manager = MavenProjectsManager.getInstance(project);

                MavenSelectProjectPopup.buildPopup(manager, p -> myWorkingDirectory.setValue(p.getDirectory()))
                    .showUnderneathOf(e);
            }
        });

        myWorkingDirectory = workDirBuilder.build();
        myWorkingDirectory.getComponent().setVisibleLength(0);

        formBuilder.addLabeled(MavenRunnerLocalize.mavenRunnerWorkingDirectory(), myWorkingDirectory.getComponent());

        EditorBoxBuilderFactory editorBoxBuilderFactory = project.getApplication().getInstance(EditorBoxBuilderFactory.class);

        EditorBoxBuilder goalsBuilder = editorBoxBuilderFactory.create(project);
        if (!project.isDefault()) {
            goalsBuilder = goalsBuilder.completion(new MavenArgumentsCompletionProvider(project));
        }
        myGoalsEditor = goalsBuilder.build();
        formBuilder.addLabeled(MavenRunnerLocalize.mavenRunnerCommandLine(), myGoalsEditor);

        if (withProfiles) {
            EditorBoxBuilder profilesBuilder = editorBoxBuilderFactory.create(project)
                .placeholder(MavenRunnerLocalize.mavenRunnerProfilesHint());
            if (!project.isDefault()) {
                profilesBuilder = profilesBuilder.completion(new ProfilesCompletionProvider(project));
            }
            myProfilesEditor = profilesBuilder.build();
            formBuilder.addLabeled(MavenRunnerLocalize.mavenRunnerProfiles(), myProfilesEditor);
        }
        else {
            myProfilesEditor = null;
        }

        if (withResolveLocal) {
            CheckBox resolveToWorkspaceCheckBox = CheckBox.create(MavenRunnerLocalize.mavenRunnerResolveWorkspaceArtifacts());
            resolveToWorkspaceCheckBox.setToolTipText(MavenRunnerLocalize.mavenRunnerResolveWorkspaceArtifactsTooltip());
            myResolveToWorkspaceCheckBox = resolveToWorkspaceCheckBox;

            formBuilder.addBottom(resolveToWorkspaceCheckBox);
        }
        else {
            myResolveToWorkspaceCheckBox = null;
        }

        myComponent = formBuilder.build();
    }

    public Component getComponent() {
        return myComponent;
    }

    public EditorBox getGoalsEditor() {
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
    protected void setData(MavenRunnerParameters data) {
        data.setWorkingDirPath(myWorkingDirectory.getValue());
        data.setGoals(ParametersListUtil.parse(StringUtil.notNullize(myGoalsEditor.getValue())));
        if (myResolveToWorkspaceCheckBox != null) {
            data.setResolveToWorkspace(myResolveToWorkspaceCheckBox.getValueOrError());
        }

        if (myProfilesEditor != null) {
            Map<String, Boolean> profilesMap = new LinkedHashMap<>();

            List<String> profiles = ParametersListUtil.parse(StringUtil.notNullize(myProfilesEditor.getValue()));

            for (String profile : profiles) {
                boolean isEnabled = true;
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
    protected void getData(MavenRunnerParameters data) {
        myWorkingDirectory.setValue(data.getWorkingDirPath());
        myGoalsEditor.setValue(ParametersList.join(data.getGoals()));

        if (myResolveToWorkspaceCheckBox != null) {
            myResolveToWorkspaceCheckBox.setValue(data.isResolveToWorkspace());
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

            myProfilesEditor.setValue(parametersList.getParametersString());
        }
    }

    private static class ProfilesCompletionProvider extends TextFieldCompletionProvider {
        private final Project myProject;

        ProfilesCompletionProvider(Project project) {
            super(true);
            myProject = project;
        }

        @Override
        public final void addCompletionVariants(String text, int offset, String prefix, CompletionResultSet result) {
            MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);
            for (String profile : manager.getAvailableProfiles()) {
                result.addElement(LookupElementBuilder.create(ParametersListUtil.join(profile)));
            }
        }

        @Override
        public String getPrefix(String currentTextPrefix) {
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
    }
}
