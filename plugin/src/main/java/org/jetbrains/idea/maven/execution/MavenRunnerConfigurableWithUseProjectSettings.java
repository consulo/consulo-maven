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

import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenDisablePanelCheckbox;
import org.jspecify.annotations.Nullable;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenRunnerConfigurableWithUseProjectSettings extends MavenRunnerConfigurable {
    private @Nullable CheckBox myUseProjectSettings;

    public MavenRunnerConfigurableWithUseProjectSettings(Project project) {
        super(project, true);
    }

    public abstract void setState(@Nullable MavenRunnerSettings state);

    @Override
    @RequiredUIAccess
    public boolean isModified() {
        CheckBox useProjectSettings = myUseProjectSettings;
        if (useProjectSettings == null) {
            return false;
        }

        if (useProjectSettings.getValueOrError()) {
            return getState() != null;
        }
        else {
            return getState() == null || super.isModified();
        }
    }

    @Override
    @RequiredUIAccess
    public void apply() {
        CheckBox useProjectSettings = myUseProjectSettings;
        if (useProjectSettings == null) {
            return;
        }

        if (useProjectSettings.getValueOrError()) {
            setState(null);
        }
        else {
            MavenRunnerSettings state = getState();
            if (state != null) {
                apply(state);
            }
            else {
                MavenRunnerSettings settings = MavenRunner.getInstance(myProject).getSettings().clone();
                apply(settings);
                setState(settings);
            }
        }
    }

    @Override
    @RequiredUIAccess
    public void reset() {
        CheckBox useProjectSettings = myUseProjectSettings;
        if (useProjectSettings == null) {
            return;
        }

        MavenRunnerSettings state = getState();
        useProjectSettings.setValue(state == null);

        if (state == null) {
            MavenRunnerSettings settings = MavenRunner.getInstance(myProject).getSettings();
            reset(settings);
        }
        else {
            reset(state);
        }
    }

    @RequiredUIAccess
    @Override
    public Component createUIComponent(Disposable uiDisposable) {
        CheckBox useProjectSettings = CheckBox.create(MavenRunnerLocalize.mavenRunnerUseProjectSettings());
        myUseProjectSettings = useProjectSettings;

        return MavenDisablePanelCheckbox.createPanel(super.createUIComponent(uiDisposable), useProjectSettings);
    }
}
