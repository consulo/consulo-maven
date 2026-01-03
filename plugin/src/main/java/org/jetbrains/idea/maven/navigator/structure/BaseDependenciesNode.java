package org.jetbrains.idea.maven.navigator.structure;

import consulo.maven.rt.server.common.model.MavenArtifactNode;
import consulo.maven.rt.server.common.model.MavenArtifactState;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseDependenciesNode extends GroupNode {
    protected final MavenProject myMavenProject;
    private List<DependencyNode> myChildren = new ArrayList<>();

    BaseDependenciesNode(MavenProjectsStructure mavenProjectsStructure, MavenSimpleNode parent, MavenProject mavenProject) {
        super(mavenProjectsStructure, parent);
        myMavenProject = mavenProject;
    }

    public MavenProject getMavenProject() {
        return myMavenProject;
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
        return myChildren;
    }

    protected void updateChildren(List<MavenArtifactNode> children, MavenProject mavenProject) {
        List<DependencyNode> newNodes = null;
        int validChildCount = 0;

        for (MavenArtifactNode each : children) {
            if (each.getState() != MavenArtifactState.ADDED && each.getState() != MavenArtifactState.CONFLICT && each.getState() != MavenArtifactState.DUPLICATE) {
                continue;
            }

            if (newNodes == null) {
                if (validChildCount < myChildren.size()) {
                    DependencyNode currentValidNode = myChildren.get(validChildCount);

                    if (currentValidNode.getArtifact().equals(each.getArtifact())) {
                        if (each.getState() == MavenArtifactState.ADDED) {
                            currentValidNode.updateChildren(each.getDependencies(), mavenProject);
                        }
                        currentValidNode.updateDependency();

                        validChildCount++;
                        continue;
                    }
                }

                newNodes = new ArrayList<>(children.size());
                newNodes.addAll(myChildren.subList(0, validChildCount));
            }

            DependencyNode newNode = findOrCreateNodeFor(each, mavenProject, validChildCount);
            newNodes.add(newNode);
            if (each.getState() == MavenArtifactState.ADDED) {
                newNode.updateChildren(each.getDependencies(), mavenProject);
            }
            newNode.updateDependency();
        }

        if (newNodes == null) {
            if (validChildCount == myChildren.size()) {
                return; // All nodes are valid, child did not changed.
            }

            assert validChildCount < myChildren.size();

            newNodes = new ArrayList<>(myChildren.subList(0, validChildCount));
        }

        myChildren = newNodes;
        childrenChanged();
    }

    private DependencyNode findOrCreateNodeFor(MavenArtifactNode artifact, MavenProject mavenProject, int from) {
        for (int i = from; i < myChildren.size(); i++) {
            DependencyNode node = myChildren.get(i);
            if (node.getArtifact().equals(artifact.getArtifact())) {
                return node;
            }
        }
        return new DependencyNode(myMavenProjectsStructure, this, artifact, mavenProject);
    }

    @Override
    public String getMenuId() {
        return "Maven.DependencyMenu";
    }
}
