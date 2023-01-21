package org.jetbrains.idea.maven.plugins.api;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.util.ProcessingContext;
import javax.annotation.Nonnull;

import consulo.language.psi.PsiReferenceBase;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenCompletionReferenceProvider implements MavenParamReferenceProvider {

  protected abstract Object[] getVariants(@Nonnull PsiReferenceBase reference);

  @Override
  public PsiReference[] getReferencesByElement(@Nonnull PsiElement element,
                                               @Nonnull MavenDomConfiguration domCfg,
                                               @Nonnull ProcessingContext context) {
    return new PsiReference[] {
      new PsiReferenceBase<PsiElement>(element, true) {
        @Override
        public PsiElement resolve() {
          return null;
        }

        @Nonnull
        @Override
        public Object[] getVariants() {
          return MavenCompletionReferenceProvider.this.getVariants(this);
        }
      }
    };
  }
}
