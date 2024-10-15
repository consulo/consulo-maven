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
package org.jetbrains.idea.maven.wizards;

import consulo.disposer.Disposable;
import consulo.maven.newProject.MavenNewModuleContext;
import consulo.maven.rt.server.common.model.MavenArchetype;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.wizard.WizardStep;
import consulo.ui.ex.wizard.WizardStepValidationException;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.execution.MavenPropertiesPanel;
import org.jetbrains.idea.maven.project.MavenEnvironmentForm;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class SelectPropertiesStep implements WizardStep<MavenNewModuleContext> {
    private final Project myProjectOrNull;

    private JPanel myMainPanel;
    private JPanel myEnvironmentPanel;
    private JPanel myPropertiesPanel;

    private MavenEnvironmentForm myEnvironmentForm;
    @Nullable
    private MavenPropertiesPanel myMavenPropertiesPanel;

    private Map<String, String> mySelectedProps = Map.of();

    public SelectPropertiesStep() {
        myProjectOrNull = null;
    }

    private void initComponents(@Nonnull Disposable uiDisposable) {
        myEnvironmentForm = new MavenEnvironmentForm();

        Project project = myProjectOrNull == null ? ProjectManager.getInstance().getDefaultProject() : myProjectOrNull;
        myEnvironmentForm.getData(MavenProjectsManager.getInstance(project).getGeneralSettings().clone());

        myEnvironmentPanel.add(myEnvironmentForm.createComponent(uiDisposable), BorderLayout.CENTER);

        myMavenPropertiesPanel = new MavenPropertiesPanel(new HashMap<>());
        myPropertiesPanel.add(myMavenPropertiesPanel);
        myMavenPropertiesPanel.setDataFromMap(mySelectedProps);
    }

    @Override
    public void onStepEnter(@Nonnull MavenNewModuleContext context) {
        MavenArchetype archetype = context.getArchetype();

        Map<String, String> props = new LinkedHashMap<String, String>();

        MavenId projectId = context.getProjectId();

        props.put("groupId", projectId.getGroupId());
        props.put("artifactId", projectId.getArtifactId());
        props.put("version", projectId.getVersion());

        props.put("archetypeGroupId", archetype.groupId);
        props.put("archetypeArtifactId", archetype.artifactId);
        props.put("archetypeVersion", archetype.version);
        if (archetype.repository != null) {
            props.put("archetypeRepository", archetype.repository);
        }

        if (myMavenPropertiesPanel != null) {
            myMavenPropertiesPanel.setDataFromMap(props);
        }
        else {
            mySelectedProps = props;
        }
    }

    @Override
    public void onStepLeave(@Nonnull MavenNewModuleContext context) {
        context.setEnvironmentForm(myEnvironmentForm);
        if (myMavenPropertiesPanel != null) {
            context.setPropertiesToCreateByArtifact(myMavenPropertiesPanel.getDataAsMap());
        }
        else {
            context.setPropertiesToCreateByArtifact(mySelectedProps);
        }
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    public Component getComponent(@Nonnull MavenNewModuleContext context, @Nonnull Disposable uiDisposable) {
        throw new UnsupportedOperationException("desktop only");
    }

    @Override
    public JComponent getSwingComponent(@Nonnull MavenNewModuleContext context, @Nonnull Disposable uiDisposable) {
        initComponents(uiDisposable);
        return myMainPanel;
    }

    @Override
    public boolean isVisible(@Nonnull MavenNewModuleContext context) {
        return context.getArchetype() != null;
    }

    @Override
    public void validateStep(@Nonnull MavenNewModuleContext context) throws WizardStepValidationException {
        File mavenHome = MavenUtil.resolveMavenHomeDirectory(myEnvironmentForm.getMavenHome());
        if (mavenHome == null) {
            throw new WizardStepValidationException("Maven home directory is not specified");
        }

        if (!MavenUtil.isValidMavenHome(mavenHome)) {
            throw new WizardStepValidationException("Maven home directory is invalid: " + mavenHome);
        }
    }
}
