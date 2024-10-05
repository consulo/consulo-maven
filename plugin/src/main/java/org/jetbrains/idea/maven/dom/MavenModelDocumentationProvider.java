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

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.documentation.LanguageDocumentationProvider;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.impl.psi.FakePsiElement;
import consulo.language.psi.ElementDescriptionLocation;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.usage.UsageViewTypeLocation;
import consulo.util.lang.StringUtil;
import consulo.xml.lang.xml.XMLLanguage;
import consulo.xml.psi.xml.XmlTag;
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper;
import org.jetbrains.idea.maven.utils.MavenLog;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public class MavenModelDocumentationProvider implements LanguageDocumentationProvider {
    @Override
    @RequiredReadAction
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return getDoc(element, false);
    }

    @Override
    @RequiredReadAction
    public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
        element = getMavenElement(element);
        if (element == null) {
            return null;
        }
        if (MavenDomUtil.isMavenProperty(element)) {
            return Collections.emptyList();
        }

        // todo hard-coded maven version
        // todo add auto-opening the element's doc
        //String name = ((PsiNamedElement)element).getName();
        return Collections.singletonList("http://maven.apache.org/ref/2.2.1/maven-model/maven.html");
    }

    @Override
    @RequiredReadAction
    public String generateDoc(PsiElement element, PsiElement originalElement) {
        return getDoc(element, true);
    }

    @Nullable
    @RequiredReadAction
    private String getDoc(PsiElement element, boolean html) {
        return getMavenElementDescription(element, DescKind.TYPE_NAME_VALUE, html);
    }

    @RequiredReadAction
    public String getElementDescription(@Nonnull PsiElement element, @Nonnull ElementDescriptionLocation location) {
        return getMavenElementDescription(element, location instanceof UsageViewTypeLocation ? DescKind.TYPE : DescKind.NAME, false);
    }

    @Nullable
    @RequiredReadAction
    private static String getMavenElementDescription(PsiElement e, DescKind kind, boolean html) {
        e = getMavenElement(e);
        if (e == null) {
            return null;
        }

        if (e instanceof FakePsiElement fakePsiElement) {
            return fakePsiElement.getPresentableText();
        }

        boolean property = MavenDomUtil.isMavenProperty(e);

        String type = property ? "Property" : "Model Property";
        if (kind == DescKind.TYPE) {
            return type;
        }

        String name = buildPropertyName(e, property);
        if (kind == DescKind.NAME) {
            return name;
        }

        if (kind == DescKind.TYPE_NAME_VALUE) {
            String br = html ? "<br>" : "\n ";
            String[] bold = html ? new String[]{
                "<b>",
                "</b>"
            } : new String[]{
                "",
                ""
            };
            String valueSuffix = "";
            if (e instanceof XmlTag tag) {
                valueSuffix = ": " + bold[0] + tag.getValue().getTrimmedText() + bold[1];
            }
            return type + br + name + valueSuffix;
        }

        MavenLog.LOG.error("unexpected desc kind: " + kind);
        return null;
    }

    @RequiredReadAction
    private static String buildPropertyName(PsiElement e, boolean property) {
        if (property) {
            return DescriptiveNameUtil.getDescriptiveName(e);
        }

        List<String> path = new ArrayList<>();
        do {
            path.add(DescriptiveNameUtil.getDescriptiveName(e));
            e = PsiTreeUtil.getParentOfType(e, XmlTag.class);
        }
        while (e != null);
        Collections.reverse(path);
        return StringUtil.join(path, ".");
    }

    @RequiredReadAction
    private static PsiElement getMavenElement(PsiElement e) {
        if (e instanceof MavenPsiElementWrapper wrapper) {
            e = wrapper.getWrappee();
        }

        return !MavenDomUtil.isMavenFile(e) || e instanceof PsiFile ? null : e;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return XMLLanguage.INSTANCE;
    }

    private enum DescKind {
        TYPE,
        NAME,
        TYPE_NAME_VALUE
    }
}
