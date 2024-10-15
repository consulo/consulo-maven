/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.LookupElementWeigher;
import consulo.language.psi.PsiElement;
import consulo.language.util.NegatingComparable;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.xml.lang.xml.XMLLanguage;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.psi.xml.XmlText;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomManager;
import consulo.xml.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.dom.MavenVersionComparable;
import org.jetbrains.idea.maven.dom.converters.MavenArtifactCoordinatesVersionConverter;
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Set;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl
public class MavenVersionCompletionContributor extends CompletionContributor {
    @Override
    public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        if (parameters.getCompletionType() != CompletionType.BASIC) {
            return;
        }

        PsiElement element = parameters.getPosition();

        PsiElement xmlText = element.getParent();
        if (!(xmlText instanceof XmlText)) {
            return;
        }

        PsiElement tagElement = xmlText.getParent();

        if (!(tagElement instanceof XmlTag)) {
            return;
        }

        XmlTag tag = (XmlTag)tagElement;

        Project project = element.getProject();

        DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);

        if (!(domElement instanceof GenericDomValue)) {
            return;
        }

        DomElement parent = domElement.getParent();

        if (parent instanceof MavenDomArtifactCoordinates
            && ((GenericDomValue)domElement).getConverter() instanceof MavenArtifactCoordinatesVersionConverter) {
            MavenDomArtifactCoordinates coordinates = (MavenDomArtifactCoordinates)parent;

            String groupId = coordinates.getGroupId().getStringValue();
            String artifactId = coordinates.getArtifactId().getStringValue();

            if (!StringUtil.isEmptyOrSpaces(groupId) && !StringUtil.isEmptyOrSpaces(artifactId)) {
                Set<String> versions = MavenProjectIndicesManager.getInstance(project).getVersions(groupId, artifactId);

                CompletionResultSet newResultSet = result.withRelevanceSorter(CompletionService.getCompletionService().emptySorter().weigh(
                    new LookupElementWeigher("mavenVersionWeigher") {
                        @Nullable
                        @Override
                        public Comparable weigh(@Nonnull LookupElement element) {
                            return new NegatingComparable(new MavenVersionComparable(element.getLookupString()));
                        }
                    }));

                for (String version : versions) {
                    newResultSet.addElement(LookupElementBuilder.create(version));
                }
            }
        }
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return XMLLanguage.INSTANCE;
    }
}
