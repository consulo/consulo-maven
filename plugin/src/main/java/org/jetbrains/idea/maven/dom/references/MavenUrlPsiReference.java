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

import javax.annotation.Nonnull;

import consulo.ide.impl.idea.ide.BrowserUtil;
import consulo.document.util.TextRange;
import consulo.language.impl.psi.FakePsiElement;
import consulo.language.psi.PsiElement;

public class MavenUrlPsiReference extends MavenPsiReference {
    public MavenUrlPsiReference(PsiElement element, String text, TextRange range) {
        super(element, text, range);
    }

    public PsiElement resolve() {
        return new FakePsiElement() {
            public PsiElement getParent() {
                return myElement;
            }

            @Override
            public String getName() {
                return myText;
            }

            @Override
            public void navigate(boolean requestFocus) {
                BrowserUtil.launchBrowser(myText);
            }
        };
    }

    @Nonnull
    public Object[] getVariants() {
        return EMPTY_ARRAY;
    }
}