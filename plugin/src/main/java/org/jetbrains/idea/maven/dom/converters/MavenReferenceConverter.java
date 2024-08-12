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
package org.jetbrains.idea.maven.dom.converters;

import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.document.util.TextRange;
import consulo.xml.util.xml.ConvertContext;
import consulo.xml.util.xml.Converter;
import consulo.xml.util.xml.CustomReferenceConverter;
import consulo.xml.util.xml.GenericDomValue;

import javax.annotation.Nonnull;

public abstract class MavenReferenceConverter<T> extends Converter<T> implements CustomReferenceConverter<T> {
    @Nonnull
    public PsiReference[] createReferences(GenericDomValue value, PsiElement element, ConvertContext context) {
        String text = value.getStringValue();
        TextRange range = ElementManipulators.getValueTextRange(element);
        return new PsiReference[]{createReference(element, text, range)};
    }

    protected abstract PsiReference createReference(PsiElement element, String text, TextRange range);
}
