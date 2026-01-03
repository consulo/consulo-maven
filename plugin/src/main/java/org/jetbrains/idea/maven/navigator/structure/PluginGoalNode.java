package org.jetbrains.idea.maven.navigator.structure;

import consulo.annotation.access.RequiredReadAction;
import consulo.navigation.Navigatable;
import consulo.navigation.NavigatableAdapter;
import consulo.util.lang.Comparing;
import consulo.xml.psi.xml.XmlElement;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.MavenIcons;
import org.jetbrains.idea.maven.dom.MavenPluginDomUtil;
import org.jetbrains.idea.maven.dom.plugin.MavenDomMojo;
import org.jetbrains.idea.maven.dom.plugin.MavenDomPluginModel;

public class PluginGoalNode extends GoalNode {
    private final String myUnqualifiedGoal;

    public PluginGoalNode(MavenProjectsStructure mavenProjectsStructure, PluginNode parent, String goal, String unqualifiedGoal, String displayName) {
        super(mavenProjectsStructure, parent, goal, displayName);
        setIcon(MavenIcons.PluginGoal);
        myUnqualifiedGoal = unqualifiedGoal;
    }

    @RequiredReadAction
    @Nullable
    @Override
    public Navigatable getNavigatable() {
        PluginNode pluginNode = (PluginNode) getParent();

        MavenDomPluginModel pluginModel = MavenPluginDomUtil.getMavenPluginModel(
            myMavenProjectsStructure.getProject(),
            pluginNode.getPlugin().getGroupId(),
            pluginNode.getPlugin().getArtifactId(),
            pluginNode.getPlugin().getVersion()
        );

        if (pluginModel == null) {
            return null;
        }

        for (MavenDomMojo mojo : pluginModel.getMojos().getMojos()) {
            final XmlElement xmlElement = mojo.getGoal().getXmlElement();

            if (xmlElement instanceof Navigatable && Comparing.equal(myUnqualifiedGoal, mojo.getGoal().getStringValue())) {
                return new NavigatableAdapter() {
                    @Override
                    public void navigate(boolean requestFocus) {
                        ((Navigatable) xmlElement).navigate(requestFocus);
                    }
                };
            }
        }

        return null;
    }
}
