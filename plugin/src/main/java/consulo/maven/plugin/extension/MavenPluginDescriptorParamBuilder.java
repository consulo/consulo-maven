package consulo.maven.plugin.extension;

import consulo.language.psi.PsiReferenceProvider;
import org.jetbrains.idea.maven.plugins.api.MavenParamLanguageProvider;
import org.jetbrains.idea.maven.plugins.api.MavenParamReferenceProvider;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
public interface MavenPluginDescriptorParamBuilder
{
	@Nonnull
	MavenPluginDescriptorParamBuilder ref(Supplier<? extends MavenParamReferenceProvider> refProvider);

	@Nonnull
	MavenPluginDescriptorParamBuilder psiRef(Supplier<? extends PsiReferenceProvider> refProvider);

	@Nonnull
	MavenPluginDescriptorParamBuilder soft();

	@Nonnull
	MavenPluginDescriptorParamBuilder values(@Nonnull String... values);

	@Nonnull
	MavenPluginDescriptorParamBuilder language(@Nonnull String languageId);

	@Nonnull
	MavenPluginDescriptorParamBuilder languageProvider(@Nonnull Supplier<? extends MavenParamLanguageProvider> languageProvider);

	@Nonnull
	MavenPluginDescriptorParamBuilder languageInjectionPrefix(@Nonnull String prefix);

	@Nonnull
	MavenPluginDescriptorParamBuilder languageInjectionSuffix(@Nonnull String suffix);
}
