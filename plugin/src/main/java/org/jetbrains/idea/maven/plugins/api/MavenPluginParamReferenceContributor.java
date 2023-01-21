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
package org.jetbrains.idea.maven.plugins.api;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.*;
import consulo.language.util.ProcessingContext;
import consulo.maven.plugin.MavenPluginDescriptorParam;
import consulo.xml.lang.xml.XMLLanguage;
import consulo.xml.patterns.XmlPatterns;
import consulo.xml.psi.xml.XmlText;
import consulo.xml.psi.xml.XmlTokenType;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;

import javax.annotation.Nonnull;
import java.util.function.BiPredicate;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl
public class MavenPluginParamReferenceContributor extends PsiReferenceContributor
{
	@Override
	public void registerReferenceProviders(PsiReferenceRegistrar registrar)
	{
		registrar.registerReferenceProvider(
				PlatformPatterns.psiElement(XmlTokenType.XML_DATA_CHARACTERS).withParent(
						XmlPatterns.xmlText().inFile(XmlPatterns.xmlFile().withName("pom.xml"))
				),
				new MavenPluginParamRefProvider());
	}

	private static class MavenPluginParamRefProvider extends PsiReferenceProvider
	{

		@Nonnull
		@Override
		public PsiReference[] getReferencesByElement(@Nonnull final PsiElement element, @Nonnull final ProcessingContext context)
		{
			final XmlText xmlText = (XmlText) element.getParent();

			if(!MavenPluginParamInfo.isSimpleText(xmlText))
			{
				return PsiReference.EMPTY_ARRAY;
			}

			class MyProcessor implements BiPredicate<MavenPluginDescriptorParam, MavenDomConfiguration>
			{
				PsiReference[] result;

				@Override
				public boolean test(MavenPluginDescriptorParam info, MavenDomConfiguration domCfg)
				{
					MavenParamReferenceProvider providerInstance = info.getRefProvider();
					if(providerInstance != null)
					{
						result = providerInstance.getReferencesByElement(xmlText, domCfg, context);
						return false;
					}

					return true;
				}
			}

			MyProcessor processor = new MyProcessor();

			MavenPluginParamInfo.processParamInfo(xmlText, processor);

			if(processor.result != null)
			{
				return processor.result;
			}

			return PsiReference.EMPTY_ARRAY;
		}
	}

	@Nonnull
	@Override
	public Language getLanguage()
	{
		return XMLLanguage.INSTANCE;
	}
}
