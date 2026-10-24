/*
 * Copyright 2013-2026 consulo.io
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

import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.language.editor.ui.EditorBox;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;

import java.util.Map;

/**
 * The profiles a {@link MavenRunConfiguration} enables or disables.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class MavenProfilesFragment implements RunConfigurationFragment<MavenRunConfiguration> {
    @Override
    public String getId() {
        return "maven.profiles";
    }

    @Override
    public LocalizeValue getName() {
        return MavenRunnerLocalize.runConfigurationFragmentProfiles();
    }

    @Override
    public boolean isVisibleByDefault(MavenRunConfiguration target) {
        return !target.getOptions().getProfiles().isEmpty();
    }

    @Override
    public void applyHidden(MavenRunConfiguration target) {
        target.getOptions().setProfiles(Map.of());
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<MavenRunConfiguration> createEditor(Project project, Disposable parent) {
        EditorBox profilesEditor = MavenRunnerParametersPanel.createProfilesEditor(project);

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(MavenRunnerLocalize.mavenRunnerProfiles(), profilesEditor);
            }

            @RequiredUIAccess
            @Override
            public void reset(MavenRunConfiguration target) {
                profilesEditor.setValue(MavenRunnerParametersPanel.formatProfiles(target.getOptions().getProfiles()));
            }

            @RequiredUIAccess
            @Override
            public void apply(MavenRunConfiguration target) {
                target.getOptions().setProfiles(MavenRunnerParametersPanel.parseProfiles(profilesEditor.getValue()));
            }
        };
    }
}
