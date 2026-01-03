package org.jetbrains.idea.maven.navigator.structure;

import org.jetbrains.idea.maven.MavenIcons;

import static org.jetbrains.idea.maven.project.ProjectBundle.message;

public class ModulesNode extends ProjectsGroupNode {
    public ModulesNode(MavenProjectsStructure structure, ProjectNode parent) {
        super(structure, parent);
        setIcon(MavenIcons.ModulesClosed);
    }

    @Override
    public String getName() {
        return message("view.node.modules");
    }
}
