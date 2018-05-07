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
package org.jetbrains.idea.maven.importing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.intellij.openapi.roots.ui.configuration.artifacts.ManifestFilesInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PackagingElementPath;
import com.intellij.packaging.impl.artifacts.PackagingElementProcessor;
import com.intellij.packaging.impl.elements.ArtifactElementType;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.packaging.ui.ManifestFileConfiguration;

/**
 * @author nik
 */
public class ArtifactExternalDependenciesImporter {
  private final ManifestFilesInfo myManifestFiles = new ManifestFilesInfo();
  private final Map<Artifact, List<PackagingElement<?>>> myExternalDependencies = new HashMap<Artifact, List<PackagingElement<?>>>();

  @javax.annotation.Nullable
  public ManifestFileConfiguration getManifestFile(@Nonnull Artifact artifact,
                                                   @Nonnull PackagingElementResolvingContext context) {
    return myManifestFiles.getManifestFile(artifact.getRootElement(), artifact.getArtifactType(), context);
  }

  public List<PackagingElement<?>> getExternalDependenciesList(@Nonnull Artifact artifact) {
    List<PackagingElement<?>> elements = myExternalDependencies.get(artifact);
    if (elements == null) {
      elements = new ArrayList<PackagingElement<?>>();
      myExternalDependencies.put(artifact, elements);
    }
    return elements;
  }

  public void applyChanges(ModifiableArtifactModel artifactModel, final PackagingElementResolvingContext context) {
    myManifestFiles.saveManifestFiles();
    final List<Pair<? extends CompositePackagingElement<?>, List<PackagingElement<?>>>> elementsToInclude =
      new ArrayList<Pair<? extends CompositePackagingElement<?>, List<PackagingElement<?>>>>();
    for (Artifact artifact : artifactModel.getArtifacts()) {
      ArtifactUtil.processPackagingElements(artifact, ArtifactElementType.getInstance(),
                                            new PackagingElementProcessor<ArtifactPackagingElement>() {
                                              @Override
                                              public boolean process(@Nonnull ArtifactPackagingElement artifactPackagingElement,
                                                                     @Nonnull PackagingElementPath path) {
                                                final Artifact included = artifactPackagingElement.findArtifact(context);
                                                final CompositePackagingElement<?> parent = path.getLastParent();
                                                if (parent != null && included != null) {
                                                  final List<PackagingElement<?>> elements = myExternalDependencies.get(included);
                                                  if (elements != null) {
                                                    elementsToInclude.add(Pair.create(parent, elements));
                                                  }
                                                }
                                                return true;
                                              }
                                            }, context, false);
    }

    for (Pair<? extends CompositePackagingElement<?>, List<PackagingElement<?>>> pair : elementsToInclude) {
      pair.getFirst().addOrFindChildren(pair.getSecond());
    }
  }
}
