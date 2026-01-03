package org.jetbrains.idea.maven.navigator.structure;

import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.MavenIcons;

import java.util.ArrayList;
import java.util.List;

public abstract class ProjectsGroupNode extends GroupNode {
    private final List<ProjectNode> myProjectNodes = new ArrayList<>();

    public ProjectsGroupNode(MavenProjectsStructure structure, MavenSimpleNode parent) {
        super(structure, parent);
        setIcon(MavenIcons.ModulesClosed);
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
        return myProjectNodes;
    }

    @TestOnly
    public List<ProjectNode> getProjectNodesInTests() {
        return myProjectNodes;
    }

    protected void add(ProjectNode projectNode) {
        projectNode.setParent(this);
        insertSorted(myProjectNodes, projectNode);

        childrenChanged();
    }

    public void remove(ProjectNode projectNode) {
        projectNode.setParent(null);
        myProjectNodes.remove(projectNode);

        childrenChanged();
    }

    public void sortProjects() {
        sort(myProjectNodes);
        childrenChanged();
    }
}
