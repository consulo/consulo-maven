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

import com.intellij.xml.XmlElementDescriptor;
import consulo.annotation.component.ExtensionImpl;
import consulo.xml.psi.impl.source.xml.XmlElementDescriptorProvider;
import consulo.xml.psi.xml.XmlTag;

@ExtensionImpl
public class MavenDomElementDescriptorProvider implements XmlElementDescriptorProvider
{
	public XmlElementDescriptor getDescriptor(XmlTag tag)
	{
		return MavenDomElementDescriptorHolder.getInstance(tag.getProject()).getDescriptor(tag);
	}
}
