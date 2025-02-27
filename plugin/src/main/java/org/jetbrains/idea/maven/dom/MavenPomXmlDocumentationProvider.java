/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.xml.util.documentation.XmlDocumentationProvider;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.documentation.DocumentationProvider;
import consulo.language.editor.documentation.LanguageDocumentationProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.util.lang.StringUtil;
import consulo.xml.lang.xml.XMLLanguage;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl
public class MavenPomXmlDocumentationProvider implements LanguageDocumentationProvider {
    private final DocumentationProvider myDelegate = new XmlDocumentationProvider() {
        @Override
        protected String generateDoc(String str, String name, String typeName, String version) {
            if (str != null) {
                str = StringUtil.unescapeXml(str);
            }

            return super.generateDoc(str, name, typeName, version);
        }
    };


    @RequiredReadAction
    private static boolean isFromPomXml(PsiElement element) {
        if (element == null) {
            return false;
        }

        PsiFile containingFile = element.getContainingFile();
        return containingFile != null && containingFile.getName().equals("maven-4.0.0.xsd");
    }

    @Nullable
    @Override
    @RequiredReadAction
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return isFromPomXml(element) ? myDelegate.getQuickNavigateInfo(element, originalElement) : null;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
        return isFromPomXml(element) ? myDelegate.getUrlFor(element, originalElement) : null;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return isFromPomXml(element) ? myDelegate.generateDoc(element, originalElement) : null;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
        return isFromPomXml(element) ? myDelegate.getDocumentationElementForLookupItem(psiManager, object, element) : null;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return XMLLanguage.INSTANCE;
    }
}
