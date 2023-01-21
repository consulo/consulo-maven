package consulo.maven.internal.plugin;

import consulo.maven.plugin.extension.MavenPluginDescriptorBuilder;
import consulo.maven.plugin.extension.MavenPluginDescriptorRegistrator;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
public class MavenPluginDescriptorRegistratorImpl implements MavenPluginDescriptorRegistrator
{
	private List<MavenPluginDescriptorBuilderImpl> myBuilders = new ArrayList<>();

	@Override
	public MavenPluginDescriptorBuilder plugin(@Nonnull String groupId, @Nonnull String artifactId)
	{
		MavenPluginDescriptorBuilderImpl builder = new MavenPluginDescriptorBuilderImpl(groupId, artifactId);
		myBuilders.add(builder);
		return builder;
	}

	public List<MavenPluginDescriptorBuilderImpl> getBuilders()
	{
		return myBuilders;
	}
}
