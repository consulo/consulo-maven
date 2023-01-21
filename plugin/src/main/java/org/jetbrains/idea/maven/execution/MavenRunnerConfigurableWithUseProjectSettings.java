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
import consulo.util.lang.Pair;
import consulo.disposer.Disposable;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.idea.maven.project.MavenDisablePanelCheckbox;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenRunnerConfigurableWithUseProjectSettings extends MavenRunnerConfigurable {

  private JCheckBox myUseProjectSettings;

  public MavenRunnerConfigurableWithUseProjectSettings(@Nonnull Project project) {
    super(project, true);
  }

  public abstract void setState(@Nullable MavenRunnerSettings state);

  @Override
  public boolean isModified() {
    if (myUseProjectSettings.isSelected()) {
      return getState() != null;
    }
    else {
      return getState() == null || super.isModified();
    }
  }

  @Override
  public void apply() {
    if (myUseProjectSettings.isSelected()) {
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
  public void reset() {
    MavenRunnerSettings state = getState();
    myUseProjectSettings.setSelected(state == null);

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
  public JComponent createComponent(@Nonnull Disposable uiDisposable) {
    Pair<JPanel,JCheckBox> pair = MavenDisablePanelCheckbox.createPanel(super.createComponent(uiDisposable), "Use project settings");

    myUseProjectSettings = pair.second;
    return pair.first;
  }
}
