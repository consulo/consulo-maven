package org.jetbrains.idea.maven.navigator.structure;

import consulo.ui.ex.awt.tree.SimpleNode;

import java.util.Collections;
import java.util.List;

public abstract class GroupNode extends MavenSimpleNode {
    public GroupNode(MavenProjectsStructure mavenProjectsStructure, MavenSimpleNode parent) {
        super(mavenProjectsStructure, parent);
    }

    @Override
    public boolean isVisible() {
        if (getDisplayKind() == MavenProjectsStructure.DisplayKind.ALWAYS) {
            return true;
        }

        for (SimpleNode each : getChildren()) {
            if (((MavenSimpleNode)each).isVisible()) {
                return true;
            }
        }
        return false;
    }

    protected <T extends MavenSimpleNode> void insertSorted(List<T> list, T newObject) {
        int pos = Collections.binarySearch(list, newObject, MavenProjectsStructure.NODE_COMPARATOR);
        list.add(pos >= 0 ? pos : -pos - 1, newObject);
    }

    protected void sort(List<? extends MavenSimpleNode> list) {
        Collections.sort(list, MavenProjectsStructure.NODE_COMPARATOR);
    }
}
