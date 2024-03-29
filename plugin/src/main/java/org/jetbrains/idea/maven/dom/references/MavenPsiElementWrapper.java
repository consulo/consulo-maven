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
package org.jetbrains.idea.maven.dom.references;

import consulo.document.util.TextRange;
import consulo.navigation.Navigatable;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.impl.psi.RenameableFakePsiElement;
import consulo.ui.image.Image;
import org.jetbrains.idea.maven.MavenIcons;

public class MavenPsiElementWrapper extends RenameableFakePsiElement {
  private final PsiElement myWrappee;
  private final Navigatable myNavigatable;

  public MavenPsiElementWrapper(PsiElement wrappeeElement, Navigatable navigatable) {
    super(wrappeeElement.getParent());
    myWrappee = wrappeeElement;
    myNavigatable = navigatable;
  }

  public PsiElement getWrappee() {
    return myWrappee;
  }

  public PsiElement getParent() {
    return myWrappee.getParent();
  }

  @Override
  public String getName() {
    return ((PsiNamedElement)myWrappee).getName();
  }

  @Override
  public void navigate(boolean requestFocus) {
    myNavigatable.navigate(requestFocus);
  }

  public String getTypeName() {
    return "Property";
  }

  public Image getIcon() {
    return MavenIcons.MavenLogo;
  }

  @Override
  public TextRange getTextRange() {
    return myWrappee.getTextRange();
  }

  @Override
  public boolean isEquivalentTo(PsiElement other) {
    if (other instanceof MavenPsiElementWrapper) {
      return myWrappee == ((MavenPsiElementWrapper)other).myWrappee;
    }
    return myWrappee == other;
  }
}
