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
package org.jetbrains.idea.maven.navigator;

import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.maven.rt.server.common.model.MavenArtifact;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.navigation.Navigatable;
import consulo.navigation.NavigatableAdapter;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlDocument;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.psi.xml.XmlTag;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependencies;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

/**
 * @author Konstantin Bulenkov
 */
public class MavenNavigationUtil {
  private static final String ARTIFACT_ID = "artifactId";

  private MavenNavigationUtil() {
  }

  @Nullable
  public static Navigatable createNavigatableForPom(final Project project, final VirtualFile file) {
    if (file == null || !file.isValid()) return null;
    final PsiFile result = PsiManager.getInstance(project).findFile(file);
    return result == null ? null : new NavigatableAdapter() {
      public void navigate(boolean requestFocus) {
        int offset = 0;
        if (result instanceof XmlFile) {
          final XmlDocument xml = ((XmlFile)result).getDocument();
          if (xml != null) {
            final XmlTag rootTag = xml.getRootTag();
            if (rootTag != null) {
              final XmlTag[] id = rootTag.findSubTags(ARTIFACT_ID, rootTag.getNamespace());
              if (id.length > 0) {
                offset = id[0].getValue().getTextRange().getStartOffset();
              }
            }
          }
        }
        navigate(project, file, offset, requestFocus);
      }
    };
  }

  @Nullable
  public static Navigatable createNavigatableForDependency(final Project project, final VirtualFile file, final MavenArtifact artifact) {
    return new NavigatableAdapter() {
      public void navigate(boolean requestFocus) {
        if (!file.isValid()) return;

        MavenDomProjectModel projectModel = MavenDomUtil.getMavenDomProjectModel(project, file);
        if (projectModel == null) return;

        MavenDomDependency dependency = findDependency(projectModel, artifact);
        if (dependency == null) return;

        XmlTag artifactId = dependency.getArtifactId().getXmlTag();
        if (artifactId == null) return;

        navigate(project, artifactId.getContainingFile().getVirtualFile(), artifactId.getTextOffset() + artifactId.getName().length() + 2, requestFocus);
      }
    };
    //final File pom = MavenArtifactUtil.getArtifactFile(myProjectsManager.getLocalRepository(), artifact.getMavenId());
    //final VirtualFile vPom;
    //if (pom.exists()) {
    //vPom = LocalFileSystem.getInstance().findFileByIoFile(pom);
    //} else {
    //  final MavenProject mp = myProjectsManager.findProject(artifact);
    //  vPom = mp == null ? null : mp.getFile();
    //}
    //if (vPom != null) {
    //  return new Navigatable.Adapter() {
    //    public void navigate(boolean requestFocus) {
    //      int offset = 0;
    //      try {
    //        int index = new String(vPom.contentsToByteArray()).indexOf("<artifactId>" + artifact.getArtifactId() + "</artifactId>");
    //        if (index != -1) {
    //          offset += index + 12;
    //        }
    //      }
    //      catch (IOException e) {//
    //      }
    //      new OpenFileDescriptor(project, vPom, offset).navigate(requestFocus);
    //    }
    //  };
    //}
    //
    //final Module m = myProjectsManager.findModule(mavenProject);
    //if (m == null) return null;
    //final OrderEntry e = MavenRootModelAdapter.findLibraryEntry(m, artifact);
    //if (e == null) return null;
    //return new Navigatable.Adapter() {
    //  public void navigate(boolean requestFocus) {
    //    ProjectSettingsService.getInstance(project).openProjectLibrarySettings(new NamedLibraryElement(m, e));
    //  }
    //};

  }

  @Nullable
  public static VirtualFile getArtifactFile(Project project, MavenId id) {
    final File file = MavenArtifactUtil.getArtifactFile(MavenProjectsManager.getInstance(project).getLocalRepository(), id);
    return file.exists() ? LocalFileSystem.getInstance().findFileByIoFile(file) : null;
  }

  @Nullable
  public static MavenDomDependency findDependency(@Nonnull MavenDomProjectModel projectDom, @Nonnull final MavenArtifact artifact) {
    MavenDomProjectProcessorUtils.SearchProcessor<MavenDomDependency, MavenDomDependencies> processor = new MavenDomProjectProcessorUtils.SearchProcessor<MavenDomDependency, MavenDomDependencies>() {
      @Nullable
      @Override
      protected MavenDomDependency find(MavenDomDependencies element) {
        for (MavenDomDependency dependency : element.getDependencies()) {
          if (Comparing.equal(artifact.getGroupId(), dependency.getGroupId().getStringValue())
              && Comparing.equal(artifact.getArtifactId(), dependency.getArtifactId().getStringValue())) {
            return dependency;
          }
        }

        return null;
      }
    };

    MavenDomProjectProcessorUtils.processDependencies(projectDom, processor);

    return processor.getResult();
  }
}
