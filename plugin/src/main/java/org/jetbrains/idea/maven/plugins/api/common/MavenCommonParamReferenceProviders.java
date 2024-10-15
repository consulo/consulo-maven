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
package org.jetbrains.idea.maven.plugins.api.common;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.path.FileReferenceSet;
import consulo.language.util.ProcessingContext;
import consulo.util.io.CharsetToolkit;
import consulo.util.lang.function.Condition;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;
import org.jetbrains.idea.maven.dom.references.MavenDependencyReferenceProvider;
import org.jetbrains.idea.maven.dom.references.MavenPathReferenceConverter;
import org.jetbrains.idea.maven.plugins.api.MavenCompletionReferenceProvider;
import org.jetbrains.idea.maven.plugins.api.MavenParamReferenceProvider;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import jakarta.annotation.Nonnull;
import java.nio.charset.Charset;

/**
 * @author Sergey Evdokimov
 */
public class MavenCommonParamReferenceProviders {
    private MavenCommonParamReferenceProviders() {
    }

    public static class FilePath implements MavenParamReferenceProvider {
        @Override
        @RequiredReadAction
        public PsiReference[] getReferencesByElement(
            @Nonnull PsiElement element,
            @Nonnull MavenDomConfiguration domCfg,
            @Nonnull ProcessingContext context
        ) {
            return MavenPathReferenceConverter.createReferences(domCfg, element, Condition.TRUE);
        }
    }

    public static class DirPath implements MavenParamReferenceProvider {
        @Override
        @RequiredReadAction
        public PsiReference[] getReferencesByElement(
            @Nonnull PsiElement element,
            @Nonnull MavenDomConfiguration domCfg,
            @Nonnull ProcessingContext context
        ) {
            return MavenPathReferenceConverter.createReferences(domCfg, element, FileReferenceSet.DIRECTORY_FILTER);
        }
    }

    public static class DependencyWithoutVersion extends MavenDependencyReferenceProvider {
        public DependencyWithoutVersion() {
            setCanHasVersion(false);
        }
    }

    public static class Encoding extends MavenCompletionReferenceProvider {

        @Override
        protected Object[] getVariants(@Nonnull PsiReferenceBase reference) {
            Charset[] charsets = CharsetToolkit.getAvailableCharsets();

            LookupElement[] res = new LookupElement[charsets.length];
            for (int i = 0; i < charsets.length; i++) {
                res[i] = LookupElementBuilder.create(charsets[i].name()).withCaseSensitivity(false);
            }

            return res;
        }
    }

    public static class Goal extends MavenCompletionReferenceProvider {
        @Override
        protected Object[] getVariants(@Nonnull PsiReferenceBase reference) {
            return MavenUtil.getPhaseVariants(MavenProjectsManager.getInstance(reference.getElement().getProject())).toArray();
        }
    }

    public static class Profile extends MavenCompletionReferenceProvider {
        @Override
        protected Object[] getVariants(@Nonnull PsiReferenceBase reference) {
            return MavenProjectsManager.getInstance(reference.getElement().getProject()).getAvailableProfiles().toArray();
        }
    }
}
