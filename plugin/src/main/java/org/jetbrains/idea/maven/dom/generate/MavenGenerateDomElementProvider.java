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
package org.jetbrains.idea.maven.dom.generate;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.actions.generate.AbstractDomGenerateProvider;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import java.util.function.Function;

/**
 * User: Sergey.Vasiliev
 */
public class MavenGenerateDomElementProvider extends AbstractDomGenerateProvider {
    private final Function<MavenDomProjectModel, DomElement> myParentFunction;

    public MavenGenerateDomElementProvider(
        final String description,
        final Class<? extends DomElement> childElementClass,
        String mappingId,
        Function<MavenDomProjectModel, DomElement> parentFunction
    ) {
        super(description, childElementClass, mappingId);
        myParentFunction = parentFunction;
    }

    @Override
    protected DomElement getParentDomElement(final Project project, final Editor editor, final PsiFile file) {
        MavenDomProjectModel domProjectModel = MavenDomUtil.getMavenDomModel(file, MavenDomProjectModel.class);

        return domProjectModel == null ? null : myParentFunction.apply(domProjectModel);
    }
}