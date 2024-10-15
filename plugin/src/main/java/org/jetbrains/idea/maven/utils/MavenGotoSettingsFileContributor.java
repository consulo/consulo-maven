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
package org.jetbrains.idea.maven.utils;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.ide.navigation.GotoFileContributor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.navigation.NavigationItem;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ExtensionImpl
public class MavenGotoSettingsFileContributor implements GotoFileContributor, DumbAware {
    @Override
    @Nonnull
    public String[] getNames(Project project, boolean includeNonProjectItems) {
        if (!includeNonProjectItems) {
            return ArrayUtil.EMPTY_STRING_ARRAY;
        }

        Set<String> result = new HashSet<>();
        for (VirtualFile each : getSettingsFiles(project)) {
            result.add(each.getName());
        }
        return ArrayUtil.toStringArray(result);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
        if (!includeNonProjectItems) {
            return NavigationItem.EMPTY_ARRAY;
        }

        List<NavigationItem> result = new ArrayList<>();
        for (VirtualFile each : getSettingsFiles(project)) {
            if (each.getName().equals(name)) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(each);
                if (psiFile != null) {
                    result.add(psiFile);
                }
            }
        }
        return result.toArray(new NavigationItem[result.size()]);
    }

    private List<VirtualFile> getSettingsFiles(Project project) {
        return MavenProjectsManager.getInstance(project).getGeneralSettings().getEffectiveSettingsFiles();
    }
}