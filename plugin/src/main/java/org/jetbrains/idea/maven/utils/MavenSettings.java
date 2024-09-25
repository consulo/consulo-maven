/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.SearchableConfigurable;
import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.inject.Inject;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerConfigurable;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.indices.MavenRepositoriesConfigurable;
import org.jetbrains.idea.maven.project.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class MavenSettings implements SearchableConfigurable.Parent, ProjectConfigurable {
    public static final String DISPLAY_NAME = "Maven";

    private final Project myProject;
    private final MavenGeneralConfigurable myConfigurable;
    private final List<Configurable> myChildren;

    @Inject
    public MavenSettings(@Nonnull Project project) {
        myProject = project;

        myConfigurable = new MavenGeneralConfigurable() {
            @Override
            protected MavenGeneralSettings getState() {
                return MavenProjectsManager.getInstance(myProject).getGeneralSettings();
            }

            @Override
            protected MavenGeneralPanel createGeneralPanel() {
                MavenGeneralPanel panel = super.createGeneralPanel();
                panel.showOverrideCompilerBox();
                return panel;
            }
        };

        myChildren = new ArrayList<>();
        myChildren.add(new MavenImportingConfigurable(myProject));
        myChildren.add(new MavenIgnoredFilesConfigurable(myProject));

        myChildren.add(new MyMavenRunnerConfigurable(project));

        //myChildren.add(new MavenTestRunningConfigurable(project));

        if (!myProject.isDefault()) {
            myChildren.add(new MavenRepositoriesConfigurable(myProject));
        }
    }

    @Nullable
    @Override
    public String getParentId() {
        return "execution";
    }

    @Override
    public boolean hasOwnContent() {
        return true;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @RequiredUIAccess
    @Override
    public JComponent createComponent(@Nonnull Disposable uiDisposable) {
        return myConfigurable.createComponent(uiDisposable);
    }

    @RequiredUIAccess
    @Override
    public boolean isModified() {
        return myConfigurable.isModified();
    }

    @RequiredUIAccess
    @Override
    public void apply() throws ConfigurationException {
        myConfigurable.apply();
    }

    @RequiredUIAccess
    @Override
    public void reset() {
        myConfigurable.reset();
    }

    @RequiredUIAccess
    @Override
    public void disposeUIResources() {
        myConfigurable.disposeUIResources();
    }

    @Nonnull
    @Override
    public Configurable[] getConfigurables() {
        return myChildren.toArray(new Configurable[myChildren.size()]);
    }

    @Override
    @Nonnull
    public String getId() {
        return "MavenSettings";
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public String getHelpTopic() {
        return myConfigurable.getHelpTopic();
    }

    public static class MyMavenRunnerConfigurable extends MavenRunnerConfigurable {
        public MyMavenRunnerConfigurable(Project project) {
            super(project, false);
        }

        @Override
        protected MavenRunnerSettings getState() {
            return MavenRunner.getInstance(myProject).getState();
        }
    }
}
