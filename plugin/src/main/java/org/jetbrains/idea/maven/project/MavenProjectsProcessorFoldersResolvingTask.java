/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

public class MavenProjectsProcessorFoldersResolvingTask extends MavenProjectsProcessorBasicTask {
  @Nonnull
  private final MavenImportingSettings myImportingSettings;
  @javax.annotation.Nullable
  private final Runnable myOnCompletion;

  public MavenProjectsProcessorFoldersResolvingTask(@Nonnull MavenProject project,
                                                    @Nonnull MavenImportingSettings importingSettings,
                                                    @Nonnull MavenProjectsTree tree,
                                                    @Nullable Runnable onCompletion) {
    super(project, tree);
    myImportingSettings = importingSettings;
    myOnCompletion = onCompletion;
  }

  public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    myTree.resolveFolders(myMavenProject, myImportingSettings, embeddersManager, console, indicator);
    if (myOnCompletion != null) myOnCompletion.run();
  }
}
