package consulo.maven.plugin.extension;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
public interface MavenPluginDescriptorBuilder
{
	@Nonnull
	MavenPluginDescriptorParamBuilder param(@Nonnull String name);
}
