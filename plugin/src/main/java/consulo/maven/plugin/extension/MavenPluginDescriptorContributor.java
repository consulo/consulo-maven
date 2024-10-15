package consulo.maven.plugin.extension;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MavenPluginDescriptorContributor
{
	void register(@Nonnull MavenPluginDescriptorRegistrator registrator);
}
