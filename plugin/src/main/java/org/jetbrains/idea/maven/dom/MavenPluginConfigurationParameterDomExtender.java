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

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.xml.psi.xml.XmlAttribute;
import consulo.xml.util.xml.XmlName;
import consulo.xml.util.xml.reflect.DomExtender;
import consulo.xml.util.xml.reflect.DomExtensionsRegistrar;
import org.jetbrains.idea.maven.dom.model.MavenDomConfigurationParameter;

import javax.annotation.Nonnull;

@ExtensionImpl
public class MavenPluginConfigurationParameterDomExtender extends DomExtender<MavenDomConfigurationParameter> {
    @Nonnull
    @Override
    public Class<MavenDomConfigurationParameter> getElementClass() {
        return MavenDomConfigurationParameter.class;
    }

    @Override
    public void registerExtensions(@Nonnull MavenDomConfigurationParameter param, @Nonnull DomExtensionsRegistrar r) {
        for (XmlAttribute each : param.getXmlTag().getAttributes()) {
            String name = each.getName();
            if (CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED.equals(name)) {
                continue;
            }
            r.registerGenericAttributeValueChildExtension(new XmlName(name), String.class);
        }
    }
}
