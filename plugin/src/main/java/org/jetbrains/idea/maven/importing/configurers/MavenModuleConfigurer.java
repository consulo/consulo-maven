/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing.configurers;

import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.module.Module;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenModuleConfigurer {

  public abstract void configure(@Nonnull MavenProject mavenProject, @Nonnull Project project, @Nullable Module module);

  /**
   * Called once after every project has been through {@link #configure}, for configurers which must apply what they
   * collected in a single step rather than per module.
   */
  public void afterConfigure(@Nonnull Project project) {
  }

  /**
   * A fresh list per import: a configurer may collect state across the modules of one import and apply it in
   * {@link #afterConfigure}, so instances must not be shared between imports or projects.
   */
  public static List<MavenModuleConfigurer> getConfigurers() {
    List<MavenModuleConfigurer> configurers = new ArrayList<>();

    for (MavenModuleConfigurer configurer : new MavenModuleConfigurer[]{
      new MavenCompilerConfigurer(),
      new MavenEncodingConfigurer(),
      new MavenAnnotationProcessorConfigurer()}) {

      if (!Boolean.parseBoolean(System.getProperty("idea.maven.disable." + configurer.getClass().getSimpleName()))) {
        configurers.add(configurer);
      }
    }

    return configurers;
  }

}
