package org.jetbrains.idea.maven.navigator.structure;

import consulo.maven.rt.server.common.model.MavenPlugin;
import org.jetbrains.idea.maven.MavenIcons;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenPluginInfo;

import java.nio.file.Path;

public class PluginNode extends GoalsGroupNode {
    private final MavenPlugin myPlugin;
    private MavenPluginInfo myPluginInfo;

    public PluginNode(MavenProjectsStructure mavenProjectsStructure, PluginsNode parent, MavenPlugin plugin) {
        super(mavenProjectsStructure, parent);
        myPlugin = plugin;

        setIcon(MavenIcons.MavenPlugin);
        updatePlugin();
    }

    public MavenPlugin getPlugin() {
        return myPlugin;
    }

    @Override
    public String getName() {
        return myPluginInfo == null ? myPlugin.getDisplayString() : myPluginInfo.getGoalPrefix();
    }

    @Override
    protected void doUpdate() {
        setNameAndTooltip(getName(), null, myPluginInfo != null ? myPlugin.getDisplayString() : null);
    }

    public void updatePlugin() {
        Path localRepository = myMavenProjectsStructure.getProjectsManager().getLocalRepositoryPath();
        if (localRepository == null) {
            // the machine of the project is not reachable right now - the goals read before stay
            return;
        }

        boolean hadPluginInfo = myPluginInfo != null;

        myPluginInfo = MavenArtifactUtil.readPluginInfo(localRepository, myPlugin.getMavenId());

        boolean hasPluginInfo = myPluginInfo != null;

        setErrorLevel(myPluginInfo == null ? MavenProjectsStructure.ErrorLevel.ERROR : MavenProjectsStructure.ErrorLevel.NONE);

        if (hadPluginInfo == hasPluginInfo) {
            return;
        }

        myGoalNodes.clear();
        if (myPluginInfo != null) {
            for (MavenPluginInfo.Mojo mojo : myPluginInfo.getMojos()) {
                myGoalNodes.add(new PluginGoalNode(myMavenProjectsStructure, this, mojo.getQualifiedGoal(), mojo.getGoal(), mojo.getDisplayName()));
            }
        }

        sort(myGoalNodes);
        myMavenProjectsStructure.updateFrom(this);
        childrenChanged();
    }

    @Override
    public boolean isVisible() {
        // show regardless absence of children
        return super.isVisible() || getDisplayKind() != MavenProjectsStructure.DisplayKind.NEVER;
    }
}
