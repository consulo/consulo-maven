/*
 * Copyright 2013 Consulo.org
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
package org.consulo.maven.module.extension;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.consulo.module.extension.MutableModuleExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author VISTALL
 * @since 15:17/12.07.13
 */
public class MavenMutableModuleExtension extends MavenModuleExtension implements MutableModuleExtension<MavenModuleExtension> {
  @NotNull
  private final MavenModuleExtension myMavenModuleExtension;

  public MavenMutableModuleExtension(@NotNull String id, @NotNull Module module, @NotNull MavenModuleExtension mavenModuleExtension) {
    super(id, module);
    myMavenModuleExtension = mavenModuleExtension;
  }

  @Nullable
  @Override
  public JComponent createConfigurablePanel(@NotNull ModifiableRootModel rootModel, @Nullable Runnable updateOnCheck) {
    return null;
  }

  @Override
  public void setEnabled(boolean val) {
    myIsEnabled = val;
  }

  @Override
  public boolean isModified() {
    return myMavenModuleExtension.isEnabled() != myIsEnabled;
  }

  @Override
  public void commit() {
    myMavenModuleExtension.commit(this);
  }
}
