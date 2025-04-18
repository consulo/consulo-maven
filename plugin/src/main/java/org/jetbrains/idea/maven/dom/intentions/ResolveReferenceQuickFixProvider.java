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
package org.jetbrains.idea.maven.dom.intentions;

import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.QuickFixActionRegistrar;
import consulo.language.editor.intention.UnresolvedReferenceQuickFixProvider;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ResolveReferenceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
    @Override
    public void registerFixes(PsiJavaCodeReferenceElement ref, QuickFixActionRegistrar registrar) {
        registrar.register(new AddMavenDependencyQuickFix(ref));
    }

    @Nonnull
    @Override
    public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
        return PsiJavaCodeReferenceElement.class;
    }
}
