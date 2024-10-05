/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import consulo.ide.navigation.GotoFileContributor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.navigation.NavigationItem;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class MavenGotoFileContributor implements GotoFileContributor {
    @Nonnull
    public String[] getNames(Project project, boolean includeNonProjectItems) {
        List<String> result = new ArrayList<>();

        for (MavenProject each : MavenProjectsManager.getInstance(project).getProjects()) {
            result.add(each.getMavenId().getArtifactId());
        }

        return ArrayUtil.toStringArray(result);
    }

    @Nonnull
    public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
        List<NavigationItem> result = new ArrayList<>();

        for (final MavenProject each : MavenProjectsManager.getInstance(project).getProjects()) {
            if (name.equals(each.getMavenId().getArtifactId())) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(each.getFile());
                if (psiFile != null) {
                    result.add(psiFile);
                }
            }
        }

        return result.toArray(new NavigationItem[result.size()]);
    }
}
