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

package org.jetbrains.idea.maven.dom.model.completion;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.util.collection.SmartList;
import consulo.xml.lang.xml.XMLLanguage;
import consulo.xml.psi.impl.source.xml.TagNameReference;
import consulo.xml.psi.xml.XmlText;
import consulo.xml.util.xml.Converter;
import consulo.xml.util.xml.ResolvingConverter;
import consulo.xml.util.xml.impl.GenericDomValueReference;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.converters.MavenSmartConverter;
import org.jetbrains.idea.maven.dom.references.MavenPropertyCompletionContributor;

import java.util.Collection;
import java.util.Collections;

@ExtensionImpl
public class MavenSmartCompletionContributor extends CompletionContributor {
    @Override
    public void fillCompletionVariants(final CompletionParameters parameters, CompletionResultSet result) {
        if (parameters.getCompletionType() != CompletionType.SMART) {
            return;
        }

        Collection<?> variants = getVariants(parameters);

        MavenPropertyCompletionContributor.addVariants(variants, result);
    }

    @Nonnull
    private static Collection<?> getVariants(CompletionParameters parameters) {
        if (!MavenDomUtil.isMavenFile(parameters.getOriginalFile())) {
            return Collections.emptyList();
        }

        SmartList<?> result = new SmartList<>();

        for (PsiReference each : getReferences(parameters)) {
            if (each instanceof TagNameReference) {
                continue;
            }

            if (each instanceof GenericDomValueReference) {
                GenericDomValueReference reference = (GenericDomValueReference)each;

                Converter converter = reference.getConverter();

                if (converter instanceof MavenSmartConverter) {
                    result.addAll(((MavenSmartConverter)converter).getSmartVariants(reference.getConvertContext()));
                }
                else if (converter instanceof ResolvingConverter) {
                    //noinspection unchecked
                    result.addAll(((ResolvingConverter)converter).getVariants(reference.getConvertContext()));
                }
            }
            else {
                //noinspection unchecked
                Collections.addAll((Collection)result, each.getVariants());
            }
        }
        return result;
    }

    @Nonnull
    private static PsiReference[] getReferences(CompletionParameters parameters) {
        PsiElement psiElement = parameters.getPosition().getParent();
        return psiElement instanceof XmlText ? psiElement.getParent().getReferences() : psiElement.getReferences();
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return XMLLanguage.INSTANCE;
    }
}