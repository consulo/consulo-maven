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

import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.util.ProcessingContext;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomManager;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPluginDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class MavenPropertyPsiReferenceProvider extends PsiReferenceProvider {
    public static final boolean SOFT_DEFAULT = false;

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull ProcessingContext context) {
        return getReferences(element, SOFT_DEFAULT);
    }

    private static boolean isElementCanContainReference(PsiElement element) {
        if (element instanceof XmlTag tag && "delimiter".equals(tag.getName())) {
            XmlTag delimitersTag = tag.getParentTag();
            if (delimitersTag != null && "delimiters".equals(delimitersTag.getName())) {
                XmlTag configurationTag = delimitersTag.getParentTag();
                if (configurationTag != null && "configuration".equals(configurationTag.getName())) {
                    DomElement configurationDom = DomManager.getDomManager(configurationTag.getProject()).getDomElement(configurationTag);
                    if (configurationDom != null && configurationDom instanceof MavenDomConfiguration mavenDomConfiguration) {
                        if (MavenPluginDomUtil.isPlugin(
                            mavenDomConfiguration,
                            "org.apache.maven.plugins",
                            "maven-resources-plugin"
                        )) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    @Nullable
    private static MavenProject findMavenProject(PsiElement element) {
        VirtualFile virtualFile = MavenDomUtil.getVirtualFile(element);
        if (virtualFile == null) {
            return null;
        }

        MavenProjectsManager manager = MavenProjectsManager.getInstance(element.getProject());
        return manager.findProject(virtualFile);
    }

    @RequiredReadAction
    public static PsiReference[] getReferences(PsiElement element, boolean isSoft) {
        TextRange textRange = ElementManipulators.getValueTextRange(element);
        if (textRange.isEmpty()) {
            return PsiReference.EMPTY_ARRAY;
        }

        String text = element.getText();

        if (StringUtil.isEmptyOrSpaces(text)) {
            return PsiReference.EMPTY_ARRAY;
        }

        if (!isElementCanContainReference(element)) {
            return PsiReference.EMPTY_ARRAY;
        }

        MavenProject mavenProject = null;
        List<PsiReference> result = null;

        Matcher matcher = MavenPropertyResolver.PATTERN.matcher(textRange.substring(text));
        while (matcher.find()) {
            String propertyName = matcher.group(1);
            int from;
            if (propertyName == null) {
                propertyName = matcher.group(2);
                from = matcher.start(2);
            }
            else {
                from = matcher.start(1);
            }

            TextRange range = TextRange.from(textRange.getStartOffset() + from, propertyName.length());

            if (result == null) {
                result = new ArrayList<>();

                mavenProject = findMavenProject(element);
                if (mavenProject == null) {
                    return PsiReference.EMPTY_ARRAY;
                }
            }

            result.add(new MavenPropertyPsiReference(mavenProject, element, propertyName, range, isSoft));
        }

        return result == null ? PsiReference.EMPTY_ARRAY : result.toArray(new PsiReference[result.size()]);
    }
}
