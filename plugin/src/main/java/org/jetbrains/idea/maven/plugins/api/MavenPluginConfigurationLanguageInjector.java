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

import javax.annotation.Nonnull;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.PairProcessor;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;

/**
 * @author Sergey Evdokimov
 */
public final class MavenPluginConfigurationLanguageInjector implements LanguageInjector {

  @Override
  public void getLanguagesToInject(@Nonnull final PsiLanguageInjectionHost host, @Nonnull final InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (!(host instanceof XmlText)) return;

    final XmlText xmlText = (XmlText)host;

    if (!MavenPluginParamInfo.isSimpleText(xmlText)) return;

    MavenPluginParamInfo.processParamInfo(xmlText, new PairProcessor<MavenPluginParamInfo.ParamInfo, MavenDomConfiguration>() {
      @Override
      public boolean process(MavenPluginParamInfo.ParamInfo info, MavenDomConfiguration configuration) {
        Language language = info.getLanguage();

        if (language == null) {
          MavenParamLanguageProvider provider = info.getLanguageProvider();
          if (provider != null) {
            language = provider.getLanguage(xmlText, configuration);
          }
        }

        if (language != null) {
          injectionPlacesRegistrar.addPlace(language, TextRange.from(0, host.getTextLength()), info.getLanguageInjectionPrefix(), info.getLanguageInjectionSuffix());
          return false;
        }
        return true;
      }
    });
  }
}
