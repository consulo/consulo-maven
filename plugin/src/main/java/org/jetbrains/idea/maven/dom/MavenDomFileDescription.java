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
package org.jetbrains.idea.maven.dom;

import consulo.xml.psi.xml.XmlFile;
import consulo.xml.util.xml.DomFileDescription;
import consulo.xml.util.xml.highlighting.DomElementsAnnotator;
import org.jetbrains.idea.maven.dom.annotator.MavenDomAnnotator;

import javax.annotation.Nonnull;

public abstract class MavenDomFileDescription<T> extends DomFileDescription<T> {
  public MavenDomFileDescription(Class<T> rootElementClass, String rootTagName) {
    super(rootElementClass, rootTagName);
  }

  @Override
  public boolean isMyFile(@Nonnull XmlFile file) {
    return MavenDomUtil.isMavenFile(file) && super.isMyFile(file);
  }

  @Override
  public DomElementsAnnotator createAnnotator() {
    return new MavenDomAnnotator();
  }
}
