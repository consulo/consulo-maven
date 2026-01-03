package org.jetbrains.idea.maven.navigator.structure;

import consulo.util.collection.ContainerUtil;

import java.util.Collections;
import java.util.List;

public class RootNode extends ProjectsGroupNode {
    private final ProfilesNode myProfilesNode;

    public RootNode(MavenProjectsStructure mavenProjectsStructure) {
        super(mavenProjectsStructure, null);
        myProfilesNode = new ProfilesNode(mavenProjectsStructure, this);
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
        return ContainerUtil.concat(Collections.singletonList(myProfilesNode), super.doGetChildren());
    }

    public void updateProfiles() {
        myProfilesNode.updateProfiles();
    }
}
