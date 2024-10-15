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
package org.jetbrains.idea.maven.project;

import consulo.configurable.SearchableConfigurable;
import consulo.disposer.Disposable;
import consulo.ui.annotation.RequiredUIAccess;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenGeneralConfigurable implements SearchableConfigurable {
    private MavenGeneralPanel myMavenGeneralPanel;

    protected abstract MavenGeneralSettings getState();

    @RequiredUIAccess
    @Nullable
    @Override
    public JComponent createComponent(@Nonnull Disposable parentDisposable) {
        if (myMavenGeneralPanel == null) {
            myMavenGeneralPanel = createGeneralPanel();
        }
        return myMavenGeneralPanel.createComponent(parentDisposable);
    }

    protected MavenGeneralPanel createGeneralPanel() {
        return new MavenGeneralPanel();
    }

    @RequiredUIAccess
    @Override
    public boolean isModified() {
        if (myMavenGeneralPanel == null) {
            return false;
        }

        MavenGeneralSettings formData = new MavenGeneralSettings();
        myMavenGeneralPanel.setData(formData);
        return !formData.equals(getState());
    }

    @RequiredUIAccess
    @Override
    public void apply() {
        myMavenGeneralPanel.setData(getState());
    }

    @RequiredUIAccess
    @Override
    public void reset() {
        myMavenGeneralPanel.getData(getState());
    }

    @RequiredUIAccess
    @Override
    public void disposeUIResources() {
        myMavenGeneralPanel = null;
    }

    @Override
    @Nullable
    public String getHelpTopic() {
        return "reference.settings.dialog.project.maven";
    }

    @Override
    @Nonnull
    public String getId() {
        return getHelpTopic();
    }
}
