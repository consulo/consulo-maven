package org.jetbrains.idea.maven.navigator.structure;

import consulo.maven.rt.server.common.model.MavenProfileKind;
import consulo.util.lang.Pair;
import org.jetbrains.idea.maven.MavenIcons;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.idea.maven.project.ProjectBundle.message;

public class ProfilesNode extends GroupNode {
    private List<ProfileNode> myProfileNodes = new ArrayList<>();

    public ProfilesNode(MavenProjectsStructure mavenProjectsStructure, MavenSimpleNode parent) {
        super(mavenProjectsStructure, parent);
        setIcon(MavenIcons.ProfilesClosed);
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
        return myProfileNodes;
    }

    @Override
    public String getName() {
        return message("view.node.profiles");
    }

    public void updateProfiles() {
        Collection<Pair<String, MavenProfileKind>> profiles = myMavenProjectsStructure.getProjectsManager().getProfilesWithStates();

        List<ProfileNode> newNodes = new ArrayList<>(profiles.size());
        for (Pair<String, MavenProfileKind> each : profiles) {
            ProfileNode node = findOrCreateNodeFor(each.first);
            node.setState(each.second);
            newNodes.add(node);
        }

        myProfileNodes = newNodes;
        sort(myProfileNodes);
        childrenChanged();
    }

    private ProfileNode findOrCreateNodeFor(String profileName) {
        for (ProfileNode each : myProfileNodes) {
            if (each.getProfileName().equals(profileName)) {
                return each;
            }
        }
        return new ProfileNode(myMavenProjectsStructure, this, profileName);
    }
}
