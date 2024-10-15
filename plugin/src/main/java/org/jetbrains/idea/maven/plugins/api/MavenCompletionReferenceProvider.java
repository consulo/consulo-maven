package org.jetbrains.idea.maven.plugins.api;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.util.ProcessingContext;

import jakarta.annotation.Nonnull;

import consulo.language.psi.PsiReferenceBase;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenCompletionReferenceProvider implements MavenParamReferenceProvider {

    protected abstract Object[] getVariants(@Nonnull PsiReferenceBase reference);

    @Override
    public PsiReference[] getReferencesByElement(
        @Nonnull PsiElement element,
        @Nonnull MavenDomConfiguration domCfg,
        @Nonnull ProcessingContext context
    ) {
        return new PsiReference[]{
            new PsiReferenceBase<>(element, true) {
                @Override
                @RequiredReadAction
                public PsiElement resolve() {
                    return null;
                }

                @Nonnull
                @Override
                @RequiredReadAction
                public Object[] getVariants() {
                    return MavenCompletionReferenceProvider.this.getVariants(this);
                }
            }
        };
    }
}
