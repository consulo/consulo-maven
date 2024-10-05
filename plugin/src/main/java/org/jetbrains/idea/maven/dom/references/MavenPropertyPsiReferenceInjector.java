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

import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.util.lang.StringUtil;
import consulo.xml.util.xml.ConvertContext;
import consulo.xml.util.xml.DomFileDescription;
import consulo.xml.util.xml.DomReferenceInjector;
import consulo.xml.util.xml.DomUtil;
import org.jetbrains.idea.maven.dom.MavenDomProjectModelDescription;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@ExtensionImpl
public class MavenPropertyPsiReferenceInjector implements DomReferenceInjector {
    @Override
    public String resolveString(@Nullable String unresolvedText, @Nonnull ConvertContext context) {
        if (StringUtil.isEmptyOrSpaces(unresolvedText)) {
            return unresolvedText;
        }
        MavenDomProjectModel model = (MavenDomProjectModel)DomUtil.getFileElement(context.getInvocationElement()).getRootElement();
        return MavenPropertyResolver.resolve(unresolvedText, model);
    }

    @Override
    @Nonnull
    public PsiReference[] inject(@Nullable String unresolvedText, @Nonnull PsiElement element, @Nonnull ConvertContext context) {
        return MavenPropertyPsiReferenceProvider.getReferences(element, true);
    }

    @Override
    public boolean isAvaliable(DomFileDescription<?> fileDescription) {
        return fileDescription instanceof MavenDomProjectModelDescription;
    }
}
