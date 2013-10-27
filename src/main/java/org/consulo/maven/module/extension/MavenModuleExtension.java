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
import org.consulo.module.extension.impl.ModuleExtensionImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author VISTALL
 * @since 15:17/12.07.13
 */
public class MavenModuleExtension extends ModuleExtensionImpl<MavenModuleExtension> {
  public MavenModuleExtension(@NotNull String id, @NotNull Module module) {
    super(id, module);
  }
}
