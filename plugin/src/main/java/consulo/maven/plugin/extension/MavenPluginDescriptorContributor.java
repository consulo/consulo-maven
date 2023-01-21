package consulo.maven.plugin.extension;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MavenPluginDescriptorContributor
{
	void register(@Nonnull MavenPluginDescriptorRegistrator registrator);
}
