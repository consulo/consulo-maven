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
package org.jetbrains.idea.maven.utils;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.template.context.BaseTemplateContextType;
import consulo.language.editor.template.context.TemplateActionContext;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.xml.psi.xml.XmlText;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.localize.MavenLocalize;

import javax.annotation.Nonnull;

@ExtensionImpl
public class MavenLiveTemplateContextType extends BaseTemplateContextType {
    public MavenLiveTemplateContextType() {
        super("MAVEN", MavenLocalize.mavenName());
    }

    @Override
    @RequiredReadAction
    public boolean isInContext(@Nonnull TemplateActionContext context) {
        PsiFile file = context.getFile();
        int offset = context.getStartOffset();
        if (!MavenDomUtil.isMavenFile(file)) {
            return false;
        }

        PsiElement element = file.findElementAt(offset);
        return element != null && PsiTreeUtil.getParentOfType(element, XmlText.class) != null;
    }
}
