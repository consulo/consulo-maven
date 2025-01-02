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
package org.jetbrains.idea.maven.execution;

import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.ValidationInfo;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;

import javax.swing.*;
import java.util.Collection;

public class MavenEditGoalDialog extends DialogWrapper {
    private final Project myProject;

    private MavenRunnerParametersPanel myRunnerParametersPanel;

    public MavenEditGoalDialog(@Nonnull Project project) {
        this(project, null);
    }

    public MavenEditGoalDialog(@Nonnull Project project, @Nullable Collection<String> history) {
        super(project, true);
        myProject = project;

        setTitle("Edit Maven Goal");
        setUpDialog();
        setModal(true);
        init();
    }

    @RequiredUIAccess
    private void setUpDialog() {
        myRunnerParametersPanel = new MavenRunnerParametersPanel(myProject, false, false);
    }

    @Nullable
    @Override
    @RequiredUIAccess
    protected ValidationInfo doValidate() {
        String value = myRunnerParametersPanel.getWorkingDirectory().getValue();
        if (StringUtil.isEmptyOrSpaces(value)) {
            return new ValidationInfo("Working directory is empty", (JComponent) TargetAWT.to(myRunnerParametersPanel.getWorkingDirectory().getComponent()));
        }
        return null;
    }

    @Nonnull
    public String getGoals() {
        return myRunnerParametersPanel.getGoalsEditor().getText();
    }

    public void setGoals(@Nonnull String goals) {
        myRunnerParametersPanel.getGoalsEditor().setText(goals);
    }

    @Nonnull
    public String getWorkDirectory() {
        return myRunnerParametersPanel.getWorkingDirectory().getValue();
    }

    public void setWorkDirectory(@Nonnull String path) {
        myRunnerParametersPanel.getWorkingDirectory().setValue(path);
    }

    public void setSelectedMavenProject(@Nullable MavenProject mavenProject) {
        setWorkDirectory(mavenProject == null ? "" : mavenProject.getDirectory());
    }

    @Override
    protected JComponent createCenterPanel() {
        return myRunnerParametersPanel.createComponent();
    }

    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        return myRunnerParametersPanel.getGoalsEditor();
    }
}
