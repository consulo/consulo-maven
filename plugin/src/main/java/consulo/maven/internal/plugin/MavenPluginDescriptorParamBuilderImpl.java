package consulo.maven.internal.plugin;

import consulo.language.psi.PsiReferenceProvider;
import consulo.maven.plugin.extension.MavenPluginDescriptorParamBuilder;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.plugins.api.MavenFixedValueReferenceProvider;
import org.jetbrains.idea.maven.plugins.api.MavenParamLanguageProvider;
import org.jetbrains.idea.maven.plugins.api.MavenParamReferenceProvider;

import jakarta.annotation.Nonnull;

import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
public class MavenPluginDescriptorParamBuilderImpl implements MavenPluginDescriptorParamBuilder
{
	public Supplier<? extends MavenParamReferenceProvider> myRefProvider = () -> null;
	public Supplier<? extends MavenParamLanguageProvider> myLanguageProvider = () -> null;
	public Boolean mySoft;
	public String myLanguageId;
	@Nullable
	public String myLanguageInjectionPrefix;
	@Nullable
	public String myLanguageInjectionSuffix;

	public MavenPluginDescriptorParamBuilderImpl()
	{
	}

	@Nonnull
	@Override
	public MavenPluginDescriptorParamBuilder ref(Supplier<? extends MavenParamReferenceProvider> refProvider)
	{
		myRefProvider = refProvider;
		return this;
	}

	@Nonnull
	@Override
	public MavenPluginDescriptorParamBuilder psiRef(Supplier<? extends PsiReferenceProvider> refProvider)
	{
		myRefProvider = () -> new PsiReferenceProviderWrapper(refProvider.get());
		return this;
	}

	@Nonnull
	@Override
	public MavenPluginDescriptorParamBuilder soft()
	{
		mySoft = true;
		return this;
	}

	@Nonnull
	@Override
	public MavenPluginDescriptorParamBuilder values(@Nonnull String... values)
	{
		if(values.length == 0)
		{
			throw new IllegalArgumentException();
		}

		myRefProvider = () -> new MavenFixedValueReferenceProvider(values);
		return this;
	}

	@Nonnull
	@Override
	public MavenPluginDescriptorParamBuilder language(@Nonnull String languageId)
	{
		myLanguageId = languageId;
		return this;
	}

	@Nonnull
	@Override
	public MavenPluginDescriptorParamBuilder languageProvider(@Nonnull Supplier<? extends MavenParamLanguageProvider> languageProvider)
	{
		myLanguageProvider = languageProvider;
		return this;
	}

	@Nonnull
	@Override
	public MavenPluginDescriptorParamBuilder languageInjectionPrefix(@Nonnull String prefix)
	{
		myLanguageInjectionPrefix = prefix;
		return this;
	}

	@Nonnull
	@Override
	public MavenPluginDescriptorParamBuilder languageInjectionSuffix(@Nonnull String suffix)
	{
		myLanguageInjectionSuffix = suffix;
		return this;
	}
}
