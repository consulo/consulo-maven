package consulo.maven.plugin;

import consulo.language.Language;
import consulo.language.LanguagePointerUtil;
import consulo.maven.internal.plugin.MavenPluginDescriptorParamBuilderImpl;
import org.jetbrains.idea.maven.plugins.api.MavenParamLanguageProvider;
import org.jetbrains.idea.maven.plugins.api.MavenParamReferenceProvider;
import org.jetbrains.idea.maven.plugins.api.MavenSoftAwareReferenceProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
public final class MavenPluginDescriptorParam
{
	private final String myName;
	private final MavenParamReferenceProvider myRefProvider;
	@Nonnull
	private final Supplier<Language> myLanguagePointer;
	@Nullable
	private final MavenParamLanguageProvider myLanguageProvider;
	@Nullable
	private final String myLanguageInjectionPrefix;
	@Nullable
	private final String myLanguageInjectionSuffix;

	public MavenPluginDescriptorParam(String name, MavenPluginDescriptorParamBuilderImpl builder)
	{
		myName = name;
		myRefProvider = builder.myRefProvider.get();
		if(builder.myLanguageId != null)
		{
			myLanguagePointer = LanguagePointerUtil.createPointer(builder.myLanguageId);
		}
		else
		{
			myLanguagePointer = () -> null;
		}

		myLanguageProvider = builder.myLanguageProvider.get();

		myLanguageInjectionPrefix = builder.myLanguageInjectionPrefix;

		myLanguageInjectionSuffix = builder.myLanguageInjectionSuffix;

		if(builder.mySoft != null)
		{
			if(myRefProvider == null)
			{
				throw new IllegalArgumentException("#soft can't not be without refProvider");
			}

			((MavenSoftAwareReferenceProvider) myRefProvider).setSoft(builder.mySoft);
		}
	}

	@Nullable
	public Language getLanguage()
	{
		return myLanguagePointer.get();
	}

	@Nullable
	public MavenParamLanguageProvider getLanguageProvider()
	{
		return myLanguageProvider;
	}

	public String getName()
	{
		return myName;
	}

	@Nullable
	public MavenParamReferenceProvider getRefProvider()
	{
		return myRefProvider;
	}

	@Nullable
	public String getLanguageInjectionPrefix()
	{
		return myLanguageInjectionPrefix;
	}

	@Nullable
	public String getLanguageInjectionSuffix()
	{
		return myLanguageInjectionSuffix;
	}
}
