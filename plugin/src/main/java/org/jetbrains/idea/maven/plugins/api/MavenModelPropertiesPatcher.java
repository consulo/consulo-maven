package org.jetbrains.idea.maven.plugins.api;

import consulo.maven.internal.plugin.MavenPluginDescriptorCache;
import consulo.maven.plugin.MavenPluginDescriptor;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.maven.rt.server.common.model.MavenPlugin;

import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

/**
 * @author Sergey Evdokimov
 */
public class MavenModelPropertiesPatcher {
    /*
     * Add properties those should be added by plugins.
     */
    public static void patch(Properties modelProperties, @Nullable Collection<MavenPlugin> plugins) {
        if (plugins == null) {
            return;
        }

        Map<MavenId, MavenPluginDescriptor> descriptors = MavenPluginDescriptorCache.getDescriptors();

        for (MavenPlugin plugin : plugins) {
            MavenPluginDescriptor descriptor = descriptors.get(new MavenId(plugin.getGroupId(), plugin.getArtifactId()));

            if (descriptor == null) {
                continue;
            }

            for (String property : descriptor.getProperties()) {
                if (!modelProperties.containsKey(property)) {
                    modelProperties.setProperty(property, "");
                }
            }
        }
    }
}
