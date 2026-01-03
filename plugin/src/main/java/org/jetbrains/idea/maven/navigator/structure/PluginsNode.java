package org.jetbrains.idea.maven.navigator.structure;

import consulo.maven.rt.server.common.model.MavenPlugin;
import consulo.platform.base.icon.PlatformIconGroup;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.jetbrains.idea.maven.project.ProjectBundle.message;

public class PluginsNode extends GroupNode {
    private final List<PluginNode> myPluginNodes = new ArrayList<>();

    public PluginsNode(MavenProjectsStructure mavenProjectsStructure, ProjectNode parent) {
        super(mavenProjectsStructure, parent);
        setIcon(PlatformIconGroup.nodesConfigfolder());
    }

    @Override
    public String getName() {
        return message("view.node.plugins");
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
        return myPluginNodes;
    }

    public void updatePlugins(MavenProject mavenProject) {
        List<MavenPlugin> plugins = mavenProject.getDeclaredPlugins();

        for (Iterator<PluginNode> itr = myPluginNodes.iterator(); itr.hasNext(); ) {
            PluginNode each = itr.next();

            if (plugins.contains(each.getPlugin())) {
                each.updatePlugin();
            }
            else {
                itr.remove();
            }
        }
        for (MavenPlugin each : plugins) {
            if (!hasNodeFor(each)) {
                myPluginNodes.add(new PluginNode(myMavenProjectsStructure, this, each));
            }
        }

        sort(myPluginNodes);
        childrenChanged();
    }

    private boolean hasNodeFor(MavenPlugin plugin) {
        for (PluginNode each : myPluginNodes) {
            if (each.getPlugin().getMavenId().equals(plugin.getMavenId())) {
                return true;
            }
        }
        return false;
    }
}
