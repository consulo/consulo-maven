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
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.execution.MavenPropertiesTable;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.project.MavenEnvironmentForm;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class SelectPropertiesStep implements WizardStep<MavenNewModuleContext> {
    private final Project myProjectOrNull;

    private @Nullable MavenEnvironmentForm myEnvironmentForm;
    private @Nullable MavenPropertiesTable myPropertiesTable;

    /**
     * Source of truth for the table - the step can be entered before its component is built.
     */
    private Map<String, String> mySelectedProps = Map.of();

    public SelectPropertiesStep() {
        myProjectOrNull = null;
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    public Component getComponent(@Nonnull MavenNewModuleContext context, @Nonnull Disposable uiDisposable) {
        MavenEnvironmentForm environmentForm = myEnvironmentForm = new MavenEnvironmentForm();

        Project project = myProjectOrNull == null ? ProjectManager.getInstance().getDefaultProject() : myProjectOrNull;
        environmentForm.getData(MavenProjectsManager.getInstance(project).getGeneralSettings().clone());

        MavenPropertiesTable propertiesTable = myPropertiesTable = new MavenPropertiesTable(new HashMap<>());
        propertiesTable.setDataFromMap(mySelectedProps);

        VerticalLayout root = VerticalLayout.create();
        root.add(environmentForm.createComponent(uiDisposable));
        root.add(LabeledLayout.create(
            MavenProjectLocalize.mavenWizardProperties(),
            DockLayout.create().center(propertiesTable.getComponent())
        ));
        return root;
    }

    @RequiredUIAccess
    @Override
    public void onStepEnter(@Nonnull MavenNewModuleContext context) {
        MavenArchetype archetype = context.getArchetype();
        if (archetype == null) {
            return;
        }

        Map<String, String> props = new LinkedHashMap<>();

        MavenId projectId = context.getProjectId();
        if (projectId != null) {
            props.put("groupId", projectId.getGroupId());
            props.put("artifactId", projectId.getArtifactId());
            props.put("version", projectId.getVersion());
        }

        props.put("archetypeGroupId", archetype.groupId);
        props.put("archetypeArtifactId", archetype.artifactId);
        props.put("archetypeVersion", archetype.version);
        if (archetype.repository != null) {
            props.put("archetypeRepository", archetype.repository);
        }

        mySelectedProps = props;

        MavenPropertiesTable propertiesTable = myPropertiesTable;
        if (propertiesTable != null) {
            propertiesTable.setDataFromMap(props);
        }
    }

    @Override
    public void onStepLeave(@Nonnull MavenNewModuleContext context) {
        context.setEnvironmentForm(myEnvironmentForm);

        MavenPropertiesTable propertiesTable = myPropertiesTable;
        context.setPropertiesToCreateByArtifact(
            propertiesTable == null ? mySelectedProps : propertiesTable.getDataAsMap()
        );
    }

    @Override
    public boolean isVisible(@Nonnull MavenNewModuleContext context) {
        return context.getArchetype() != null;
    }

    @Override
    public void validateStep(@Nonnull MavenNewModuleContext context) throws WizardStepValidationException {
        MavenEnvironmentForm environmentForm = myEnvironmentForm;
        if (environmentForm == null) {
            return;
        }

        File mavenHome = MavenUtil.resolveMavenHomeDirectory(environmentForm.getMavenHome());
        if (mavenHome == null) {
            throw new WizardStepValidationException(MavenProjectLocalize.mavenWizardNoMavenHome().get());
        }

        if (!MavenUtil.isValidMavenHome(mavenHome)) {
            throw new WizardStepValidationException(
                MavenProjectLocalize.mavenWizardInvalidMavenHome(mavenHome.getPath()).get()
            );
        }
    }
}
