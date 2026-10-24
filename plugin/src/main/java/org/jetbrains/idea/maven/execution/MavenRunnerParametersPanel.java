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

import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
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

    private @Nullable Component myComponent;

    @RequiredUIAccess
    public MavenRunnerParametersPanel(Project project) {
        this(project, true, true);
    }

    @RequiredUIAccess
    public MavenRunnerParametersPanel(Project project, boolean withResolveLocal, boolean withProfiles) {
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

        EditorBoxBuilderFactory editorBoxBuilderFactory = project.getApplication().getInstance(EditorBoxBuilderFactory.class);

        EditorBoxBuilder goalsBuilder = editorBoxBuilderFactory.create(project);
        if (!project.isDefault()) {
            goalsBuilder = goalsBuilder.completion(new MavenArgumentsCompletionProvider(project));
        }
        myGoalsEditor = goalsBuilder.build();

        myProfilesEditor = withProfiles ? createProfilesEditor(project) : null;

        if (withResolveLocal) {
            CheckBox resolveToWorkspaceCheckBox = CheckBox.create(MavenRunnerLocalize.mavenRunnerResolveWorkspaceArtifacts());
            resolveToWorkspaceCheckBox.setToolTipText(MavenRunnerLocalize.mavenRunnerResolveWorkspaceArtifactsTooltip());
            myResolveToWorkspaceCheckBox = resolveToWorkspaceCheckBox;
        }
        else {
            myResolveToWorkspaceCheckBox = null;
        }
    }

    /**
     * The editor of the profiles to enable, a profile prefixed with "-" or "!" is disabled.
     */
    @RequiredUIAccess
    public static EditorBox createProfilesEditor(Project project) {
        EditorBoxBuilder profilesBuilder = project.getApplication().getInstance(EditorBoxBuilderFactory.class)
            .create(project)
            .placeholder(MavenRunnerLocalize.mavenRunnerProfilesHint());
        if (!project.isDefault()) {
            profilesBuilder = profilesBuilder.completion(new ProfilesCompletionProvider(project));
        }
        return profilesBuilder.build();
    }

    public static Map<String, Boolean> parseProfiles(@Nullable String text) {
        Map<String, Boolean> profilesMap = new LinkedHashMap<>();

        List<String> profiles = ParametersListUtil.parse(StringUtil.notNullize(text));

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
        return profilesMap;
    }

    public static String formatProfiles(Map<String, Boolean> profiles) {
        ParametersList parametersList = new ParametersList();

        for (Map.Entry<String, Boolean> entry : profiles.entrySet()) {
            String profileName = entry.getKey();

            if (!entry.getValue()) {
                profileName = '-' + profileName;
            }

            parametersList.add(profileName);
        }

        return parametersList.getParametersString();
    }

    @RequiredUIAccess
    public void addRows(RunConfigurationFragmentLayout layout) {
        layout.addLabeled(MavenRunnerLocalize.mavenRunnerWorkingDirectory(), myWorkingDirectory.getComponent());
        layout.addLabeled(MavenRunnerLocalize.mavenRunnerCommandLine(), myGoalsEditor);
        if (myProfilesEditor != null) {
            layout.addLabeled(MavenRunnerLocalize.mavenRunnerProfiles(), myProfilesEditor);
        }
        if (myResolveToWorkspaceCheckBox != null) {
            layout.addUnlabeled(myResolveToWorkspaceCheckBox);
        }
    }

    @RequiredUIAccess
    public Component getComponent() {
        Component component = myComponent;
        if (component == null) {
            FormBuilder formBuilder = FormBuilder.create();
            formBuilder.addLabeled(MavenRunnerLocalize.mavenRunnerWorkingDirectory(), myWorkingDirectory.getComponent());
            formBuilder.addLabeled(MavenRunnerLocalize.mavenRunnerCommandLine(), myGoalsEditor);
            if (myProfilesEditor != null) {
                formBuilder.addLabeled(MavenRunnerLocalize.mavenRunnerProfiles(), myProfilesEditor);
            }
            if (myResolveToWorkspaceCheckBox != null) {
                formBuilder.addBottom(myResolveToWorkspaceCheckBox);
            }
            component = formBuilder.build();
            myComponent = component;
        }
        return component;
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
            data.setProfilesMap(parseProfiles(myProfilesEditor.getValue()));
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
            myProfilesEditor.setValue(formatProfiles(data.getProfilesMap()));
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
