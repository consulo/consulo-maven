package consulo.maven.internal.plugin;

import consulo.maven.plugin.extension.MavenPluginDescriptorBuilder;
import consulo.maven.plugin.extension.MavenPluginDescriptorParamBuilder;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
public class MavenPluginDescriptorBuilderImpl implements MavenPluginDescriptorBuilder
{
	private final String myGroupId;
	private final String myArtifactId;

	private Map<String, MavenPluginDescriptorParamBuilderImpl> myParams = new LinkedHashMap<>();

	public MavenPluginDescriptorBuilderImpl(String groupId, String artifactId)
	{
		myArtifactId = artifactId;
		myGroupId = groupId;
	}

	public String getGroupId()
	{
		return myGroupId;
	}

	public String getArtifactId()
	{
		return myArtifactId;
	}

	@Nonnull
	@Override
	public MavenPluginDescriptorParamBuilder param(@Nonnull String name)
	{
		return myParams.computeIfAbsent(name, s -> new MavenPluginDescriptorParamBuilderImpl());
	}

	public Map<String, MavenPluginDescriptorParamBuilderImpl> getParams()
	{
		return myParams;
	}
}
