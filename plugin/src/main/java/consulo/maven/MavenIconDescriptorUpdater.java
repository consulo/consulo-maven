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

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.ide.IconDescriptor;
import consulo.ide.IconDescriptorUpdater;
import icons.MavenIcons;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 15:13/20.07.13
 */
public class MavenIconDescriptorUpdater implements IconDescriptorUpdater
{
  @RequiredReadAction
  @Override
  public void updateIcon(@Nonnull IconDescriptor iconDescriptor, @Nonnull PsiElement element, int flags) {
    if(element instanceof PsiFile && !DumbService.getInstance(element.getProject()).isDumb()) {
      final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if(virtualFile == null) {
        return;
      }
      if (MavenProjectsManager.getInstance(element.getProject()).findProject(virtualFile) != null) {
        iconDescriptor.setMainIcon((MavenIcons.MavenLogo));
      }
    }
  }
}
