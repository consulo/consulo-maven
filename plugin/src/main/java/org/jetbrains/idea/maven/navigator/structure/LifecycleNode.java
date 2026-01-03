package org.jetbrains.idea.maven.navigator.structure;

import consulo.platform.base.icon.PlatformIconGroup;

import static org.jetbrains.idea.maven.project.ProjectBundle.message;

public class LifecycleNode extends GoalsGroupNode {

    public LifecycleNode(MavenProjectsStructure mavenProjectsStructure, ProjectNode parent) {
        super(mavenProjectsStructure, parent);

        for (String goal : MavenProjectsStructure.PHASES) {
            myGoalNodes.add(new StandardGoalNode(mavenProjectsStructure, this, goal));
        }
        setIcon(PlatformIconGroup.nodesConfigfolder());
    }

    @Override
    public String getName() {
        return message("view.node.lifecycle");
    }

    public void updateGoalsList() {
        childrenChanged();
    }
}
