package consulo.maven.plugin.extension;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
public interface MavenPluginDescriptorRegistrator
{
	MavenPluginDescriptorBuilder plugin(@Nonnull String groupId, @Nonnull String artifactId);
}
