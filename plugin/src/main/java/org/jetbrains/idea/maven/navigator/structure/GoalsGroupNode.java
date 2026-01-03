package org.jetbrains.idea.maven.navigator.structure;

import java.util.ArrayList;
import java.util.List;

public abstract class GoalsGroupNode extends GroupNode {
    protected final List<GoalNode> myGoalNodes = new ArrayList<>();

    public GoalsGroupNode(MavenProjectsStructure mavenProjectsStructure, MavenSimpleNode parent) {
        super(mavenProjectsStructure, parent);
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
        return myGoalNodes;
    }
}
