package consulo.maven.plugin.extension;

import javax.annotation.Nonnull;
import java.io.Closeable;

/**
 * Implements {@link Closeable} just for using inside try(){}
 *
 * @author VISTALL
 * @since 20/01/2023
 */
public interface MavenPluginDescriptorBuilder extends Closeable
{
	@Nonnull
	MavenPluginDescriptorParamBuilder param(@Nonnull String name);

	@Nonnull
	MavenPluginDescriptorBuilder property(@Nonnull String... properties);

	void close();
}
