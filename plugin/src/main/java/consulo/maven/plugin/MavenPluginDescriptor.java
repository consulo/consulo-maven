package consulo.maven.plugin;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
public final class MavenPluginDescriptor
{
	private final String myGroupId;
	private final String myArtifactId;

	private final Set<String> myProperties;

	private final Map<String, MavenPluginDescriptorParam> myParams = new HashMap<>();

	public MavenPluginDescriptor(String groupId, String artifactId, Map<String, MavenPluginDescriptorParam> params, Set<String> properties)
	{
		myGroupId = groupId;
		myArtifactId = artifactId;
		myParams.putAll(params);
		myProperties = Set.copyOf(properties);
	}

	public String getGroupId()
	{
		return myGroupId;
	}

	public String getArtifactId()
	{
		return myArtifactId;
	}

	public Iterable<String> getParams()
	{
		return myParams.keySet();
	}

	@Nullable
	public MavenPluginDescriptorParam getParam(String key)
	{
		return myParams.get(key);
	}

	public Set<String> getProperties()
	{
		return myProperties;
	}
}
