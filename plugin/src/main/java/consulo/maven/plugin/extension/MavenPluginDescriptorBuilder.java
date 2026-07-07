package consulo.maven.plugin.extension;

import jakarta.annotation.Nonnull;
import java.io.Closeable;

/**
 * Implements {@link Closeable} just for using inside try(){}
 *
 * @author VISTALL
 * @since 2023-01-20
 */
public interface MavenPluginDescriptorBuilder extends Closeable
{
	@Nonnull
	MavenPluginDescriptorParamBuilder param(@Nonnull String name);

	@Nonnull
	MavenPluginDescriptorBuilder property(@Nonnull String... properties);

	@Override
    void close();
}
