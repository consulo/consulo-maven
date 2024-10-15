package consulo.maven.plugin.extension.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.maven.plugin.extension.MavenPluginDescriptorBuilder;
import consulo.maven.plugin.extension.MavenPluginDescriptorContributor;
import consulo.maven.plugin.extension.MavenPluginDescriptorRegistrator;
import org.jetbrains.idea.maven.dom.references.MavenDependencyReferenceProvider;
import org.jetbrains.idea.maven.plugins.api.common.MavenCommonParamReferenceProviders;

import jakarta.annotation.Nonnull;

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
		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.apache.maven.plugins", "maven-surefire-plugin"))
		{
			plugin.param("additionalClasspathElements").ref(MavenCommonParamReferenceProviders.DirPath::new);
			plugin.param("additionalClasspathElement").ref(MavenCommonParamReferenceProviders.DirPath::new);
			plugin.param("classpathDependencyExclude").psiRef(MavenCommonParamReferenceProviders.DependencyWithoutVersion::new);
			plugin.param("classpathDependencyExcludes").psiRef(MavenCommonParamReferenceProviders.DependencyWithoutVersion::new);
			plugin.param("forkMode").soft().values("never", "once", "always", "perthread");
			plugin.param("junitArtifactName").psiRef(MavenCommonParamReferenceProviders.DependencyWithoutVersion::new);
			plugin.param("reportFormat").values("brief", "plain").soft();
			plugin.param("runOrder").values("alphabetical", "reversealphabetical", "random", "hourly", "failedfirst", "balanced", "filesystem").soft();
			plugin.param("testNGArtifactName").psiRef(MavenCommonParamReferenceProviders.DependencyWithoutVersion::new);
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("ru.concerteza.buildnumber", "maven-jgit-buildnumber-plugin"))
		{
			plugin.property("git.revision", "git.buildnumber", "git.commitsCount", "git.tag", "git.branch");
			plugin.param("javaScriptBuildnumberCallback").language("JavaScript").languageInjectionPrefix("function() {return a + ").languageInjectionSuffix("}");
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.codehaus.mojo", "sql-maven-plugin"))
		{
			plugin.param("sqlCommand").language("SQL");
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.apache.maven.plugins", "maven-resources-plugin"))
		{
			plugin.param("encoding").ref(MavenCommonParamReferenceProviders.Encoding::new);
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.apache.maven.plugins", "maven-changelog-plugin"))
		{
			plugin.param("connectionType").values("connection", "developerConnection").soft();
			plugin.param("issueIDRegexPattern").language("RegExp");
			plugin.param("outputEncoding").ref(MavenCommonParamReferenceProviders.Encoding::new);
			plugin.param("goal").ref(MavenCommonParamReferenceProviders.Goal::new);
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.apache.maven.plugins", "maven-compiler-plugin"))
		{
			plugin.param("compilerReuseStrategy").values("reuseCreated", "reuseSame", "alwaysNew");
			plugin.param("proc").values("none", "both", "only");
			plugin.param("encoding").ref(MavenCommonParamReferenceProviders.Encoding::new);
			plugin.param("filter").ref(MavenCommonParamReferenceProviders.FilePath::new);
			plugin.param("filters").ref(MavenCommonParamReferenceProviders.FilePath::new);
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.apache.maven.plugins", "maven-rar-plugin"))
		{
			plugin.param("outputDirectory").ref(MavenCommonParamReferenceProviders.DirPath::new);
			plugin.param("workDirectory").ref(MavenCommonParamReferenceProviders.DirPath::new);
			plugin.param("encoding").ref(MavenCommonParamReferenceProviders.Encoding::new);
			plugin.param("filter").ref(MavenCommonParamReferenceProviders.FilePath::new);
			plugin.param("filters").ref(MavenCommonParamReferenceProviders.FilePath::new);
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.apache.maven.plugins", "maven-acr-plugin"))
		{
			plugin.param("filter").ref(MavenCommonParamReferenceProviders.FilePath::new);
			plugin.param("filters").ref(MavenCommonParamReferenceProviders.FilePath::new);
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.apache.maven.plugins", "maven-ejb-plugin"))
		{
			plugin.param("filter").ref(MavenCommonParamReferenceProviders.FilePath::new);
			plugin.param("filters").ref(MavenCommonParamReferenceProviders.FilePath::new);
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.apache.maven.plugins", "maven-war-plugin"))
		{
			plugin.param("outputDirectory").ref(MavenCommonParamReferenceProviders.DirPath::new);
			plugin.param("filter").ref(MavenCommonParamReferenceProviders.FilePath::new);
			plugin.param("filters").ref(MavenCommonParamReferenceProviders.FilePath::new);
			plugin.param("resourceEncoding").ref(MavenCommonParamReferenceProviders.Encoding::new);
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.apache.maven.plugins", "maven-ear-plugin"))
		{
			plugin.param("outputDirectory").ref(MavenCommonParamReferenceProviders.DirPath::new);
			plugin.param("filter").ref(MavenCommonParamReferenceProviders.FilePath::new);
			plugin.param("filters").ref(MavenCommonParamReferenceProviders.FilePath::new);
			plugin.param("applicationXml").ref(MavenCommonParamReferenceProviders.FilePath::new);
			plugin.param("encoding").ref(MavenCommonParamReferenceProviders.Encoding::new);
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"))
		{
			plugin.param("sourceDirectory").ref(MavenCommonParamReferenceProviders.DirPath::new);
			plugin.param("configLocation").ref(MavenCommonParamReferenceProviders.FilePath::new);
			plugin.param("encoding").ref(MavenCommonParamReferenceProviders.Encoding::new);
			plugin.param("outputFileFormat").values("plain", "xml").soft();
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.apache.maven.plugins", "maven-invoker-plugin"))
		{
			plugin.param("extraArtifact").psiRef(MavenDependencyReferenceProvider::new);
			plugin.param("extraArtifacts").psiRef(MavenDependencyReferenceProvider::new);
			plugin.param("encoding").ref(MavenCommonParamReferenceProviders.Encoding::new);
			plugin.param("goal").ref(MavenCommonParamReferenceProviders.Goal::new);
			plugin.param("goals").ref(MavenCommonParamReferenceProviders.Goal::new);
			plugin.param("invokerPropertiesFile").ref(MavenCommonParamReferenceProviders.FilePath::new);
			plugin.param("profile").ref(MavenCommonParamReferenceProviders.Profile::new);
			plugin.param("profiles").ref(MavenCommonParamReferenceProviders.Profile::new);
		}

		try (MavenPluginDescriptorBuilder plugin = registrator.plugin("org.apache.maven.plugins", "maven-failsafe-plugin"))
		{
			plugin.param("classpathDependencyExclude").psiRef(MavenCommonParamReferenceProviders.DependencyWithoutVersion::new);
			plugin.param("classpathDependencyExcludes").psiRef(MavenCommonParamReferenceProviders.DependencyWithoutVersion::new);
			plugin.param("encoding").ref(MavenCommonParamReferenceProviders.Encoding::new);
			plugin.param("forkMode").values("never", "once", "always", "perthread", "none", "pertest").soft();
			plugin.param("junitArtifactName").psiRef(MavenCommonParamReferenceProviders.DependencyWithoutVersion::new);
			plugin.param("testNGArtifactName").psiRef(MavenCommonParamReferenceProviders.DependencyWithoutVersion::new);
			plugin.param("reportFormat").values("brief", "plain").soft();
			plugin.param("runOrder").values("alphabetical", "reversealphabetical", "random", "hourly", "failedfirst", "balanced", "filesystem").soft();
		}
	}
}
