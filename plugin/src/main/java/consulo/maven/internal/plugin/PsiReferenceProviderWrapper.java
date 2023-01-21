package consulo.maven.internal.plugin;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.util.ProcessingContext;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;
import org.jetbrains.idea.maven.plugins.api.MavenParamReferenceProvider;
import org.jetbrains.idea.maven.plugins.api.MavenSoftAwareReferenceProvider;

import javax.annotation.Nonnull;

public class PsiReferenceProviderWrapper implements MavenParamReferenceProvider, MavenSoftAwareReferenceProvider
{
	private final PsiReferenceProvider myProvider;

	public PsiReferenceProviderWrapper(PsiReferenceProvider provider)
	{
		this.myProvider = provider;
	}

	@Override
	public PsiReference[] getReferencesByElement(@Nonnull PsiElement element,
												 @Nonnull MavenDomConfiguration domCfg,
												 @Nonnull ProcessingContext context)
	{
		return myProvider.getReferencesByElement(element, context);
	}

	@Override
	public void setSoft(boolean soft)
	{
		((MavenSoftAwareReferenceProvider) myProvider).setSoft(soft);
	}
}
