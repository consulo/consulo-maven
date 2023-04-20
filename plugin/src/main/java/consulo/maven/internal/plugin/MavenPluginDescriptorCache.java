package consulo.maven.internal.plugin;

import consulo.application.Application;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.maven.plugin.MavenPluginDescriptor;
import consulo.maven.plugin.MavenPluginDescriptorParam;
import consulo.maven.plugin.extension.MavenPluginDescriptorContributor;
import consulo.maven.rt.server.common.model.MavenId;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author VISTALL
 * @since 20/01/2023
 */
public class MavenPluginDescriptorCache
{
	private static final ExtensionPointCacheKey<MavenPluginDescriptorContributor, Map<MavenId, MavenPluginDescriptor>> CACHE_KEY = ExtensionPointCacheKey.create("MavenPluginDescriptorCache", walker
			-> {
		MavenPluginDescriptorRegistratorImpl registrator = new MavenPluginDescriptorRegistratorImpl();

		walker.walk(contributor -> contributor.register(registrator));

		Map<MavenId, MavenPluginDescriptor> descriptors = new HashMap<>();

		for(MavenPluginDescriptorBuilderImpl builder : registrator.getBuilders())
		{
			Map<String, MavenPluginDescriptorParam> params = new LinkedHashMap<>();
			for(Map.Entry<String, MavenPluginDescriptorParamBuilderImpl> entry : builder.getParams().entrySet())
			{
				params.put(entry.getKey(), new MavenPluginDescriptorParam(entry.getKey(), entry.getValue()));
			}

			MavenPluginDescriptor descriptor = new MavenPluginDescriptor(builder.getGroupId(), builder.getArtifactId(), params, builder.getProperties());

			descriptors.put(new MavenId(builder.getGroupId(), builder.getArtifactId(), null), descriptor);
		}

		return descriptors;
	});

	@Nonnull
	public static Map<MavenId, MavenPluginDescriptor> getDescriptors()
	{
		return Application.get().getExtensionPoint(MavenPluginDescriptorContributor.class).getOrBuildCache(CACHE_KEY);
	}
}
