package org.jetbrains.idea.maven.navigator.structure;

import consulo.application.AllIcons;
import org.jetbrains.idea.maven.project.MavenProject;

import static org.jetbrains.idea.maven.project.ProjectBundle.message;

public class DependenciesNode extends BaseDependenciesNode {
    public DependenciesNode(MavenProjectsStructure structure, ProjectNode parent, MavenProject mavenProject) {
        super(structure, parent, mavenProject);
        setIcon(AllIcons.Nodes.PpLibFolder);
    }

    @Override
    public String getName() {
        return message("view.node.dependencies");
    }

    public void updateDependencies() {
        updateChildren(myMavenProject.getDependencyTree(), myMavenProject);
    }
}
