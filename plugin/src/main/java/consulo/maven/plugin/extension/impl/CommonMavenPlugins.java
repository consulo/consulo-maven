package consulo.maven.plugin.extension.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.maven.plugin.extension.MavenPluginDescriptorBuilder;
import consulo.maven.plugin.extension.MavenPluginDescriptorContributor;
import consulo.maven.plugin.extension.MavenPluginDescriptorRegistrator;
import org.jetbrains.idea.maven.plugins.api.common.MavenCommonParamReferenceProviders;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
@ExtensionImpl
public class CommonMavenPlugins implements MavenPluginDescriptorContributor
{
	@Override
	public void register(@Nonnull MavenPluginDescriptorRegistrator registrator)
	{
		MavenPluginDescriptorBuilder surefirePlugin = registrator.plugin("org.apache.maven.plugins", "maven-surefire-plugin");
		surefirePlugin.param("additionalClasspathElements").ref(MavenCommonParamReferenceProviders.DirPath::new);
		surefirePlugin.param("additionalClasspathElement").ref(MavenCommonParamReferenceProviders.DirPath::new);
		surefirePlugin.param("classpathDependencyExclude").psiRef(MavenCommonParamReferenceProviders.DependencyWithoutVersion::new);
		surefirePlugin.param("classpathDependencyExcludes").psiRef(MavenCommonParamReferenceProviders.DependencyWithoutVersion::new);
		surefirePlugin.param("forkMode").soft().values("never", "once", "always", "perthread");
		surefirePlugin.param("junitArtifactName").psiRef(MavenCommonParamReferenceProviders.DependencyWithoutVersion::new);
		surefirePlugin.param("reportFormat").values("brief", "plain").soft();
		surefirePlugin.param("runOrder").values("alphabetical", "reversealphabetical", "random", "hourly", "failedfirst", "balanced", "filesystem").soft();
		surefirePlugin.param("testNGArtifactName").psiRef(MavenCommonParamReferenceProviders.DependencyWithoutVersion::new);
	}
}
