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
package consulo.maven;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.maven.icon.MavenIconGroup;
import consulo.project.DumbService;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * @author VISTALL
 * @since 15:13/20.07.13
 */
@ExtensionImpl(order = "after xml")
public class MavenIconDescriptorUpdater implements IconDescriptorUpdater {
    private final Provider<DumbService> myDumbServiceProvider;
    private final Provider<MavenProjectsManager> myMavenProjectsManagerProvider;

    @Inject
    public MavenIconDescriptorUpdater(Provider<DumbService> dumbServiceProvider,
                                      Provider<MavenProjectsManager> mavenProjectsManagerProvider) {
        myDumbServiceProvider = dumbServiceProvider;
        myMavenProjectsManagerProvider = mavenProjectsManagerProvider;
    }

    @RequiredReadAction
    @Override
    public void updateIcon(@Nonnull IconDescriptor iconDescriptor, @Nonnull PsiElement element, int flags) {
        if (element instanceof PsiFile && !myDumbServiceProvider.get().isDumb()) {
            final VirtualFile virtualFile = ((PsiFile) element).getVirtualFile();
            if (virtualFile == null) {
                return;
            }

            if (myMavenProjectsManagerProvider.get().findProject(virtualFile) != null) {
                iconDescriptor.setMainIcon(MavenIconGroup.mavenlogo());
            }
        }
    }
}
