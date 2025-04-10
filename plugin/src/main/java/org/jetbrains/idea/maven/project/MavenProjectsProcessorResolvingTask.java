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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.project.Project;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

public class MavenProjectsProcessorResolvingTask extends MavenProjectsProcessorBasicTask {
    @Nonnull
    private final MavenGeneralSettings myGeneralSettings;
    @Nullable
    private final Runnable myOnCompletion;
    @Nonnull
    private final ResolveContext myContext;

    public MavenProjectsProcessorResolvingTask(
        @Nonnull MavenProject project,
        @Nonnull MavenProjectsTree tree,
        @Nonnull MavenGeneralSettings generalSettings,
        @Nullable Runnable onCompletion,
        @Nonnull ResolveContext context
    ) {
        super(project, tree);
        myGeneralSettings = generalSettings;
        myOnCompletion = onCompletion;
        myContext = context;
    }

    @Override
    public void perform(
        Project project,
        MavenEmbeddersManager embeddersManager,
        MavenConsole console,
        MavenProgressIndicator indicator
    ) throws MavenProcessCanceledException {
        myTree.resolve(project, myMavenProject, myGeneralSettings, embeddersManager, console, myContext, indicator);
        if (myOnCompletion != null) {
            myOnCompletion.run();
        }
    }
}
