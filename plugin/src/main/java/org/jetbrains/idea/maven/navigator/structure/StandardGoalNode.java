package org.jetbrains.idea.maven.navigator.structure;

public class StandardGoalNode extends GoalNode {
    public StandardGoalNode(MavenProjectsStructure mavenProjectsStructure, GoalsGroupNode parent, String goal) {
        super(mavenProjectsStructure, parent, goal, goal);
    }

    @Override
    public boolean isVisible() {
        if (myMavenProjectsStructure.showOnlyBasicPhases() && !MavenProjectsStructure.BASIC_PHASES.contains(getGoal())) {
            return false;
        }
        return super.isVisible();
    }
}
