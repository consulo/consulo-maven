package org.jetbrains.idea.maven.navigator.structure;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.AllIcons;
import consulo.maven.rt.server.common.model.*;
import consulo.navigation.Navigatable;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil;
import org.jetbrains.idea.maven.project.MavenProject;

public class DependencyNode extends BaseDependenciesNode {
    private final MavenArtifact myArtifact;
    private final MavenArtifactNode myArtifactNode;

    public DependencyNode(MavenProjectsStructure mavenProjectsStructure, MavenSimpleNode parent, MavenArtifactNode artifactNode, MavenProject mavenProject) {
        super(mavenProjectsStructure, parent, mavenProject);
        myArtifactNode = artifactNode;
        myArtifact = artifactNode.getArtifact();
        setIcon(AllIcons.Nodes.PpLib);
    }

    public MavenArtifact getArtifact() {
        return myArtifact;
    }

    @Override
    public String getName() {
        return myArtifact.getDisplayStringForLibraryName();
    }

    private String getToolTip() {
        final StringBuilder myToolTip = new StringBuilder("");
        String scope = myArtifactNode.getOriginalScope();

        if (StringUtil.isNotEmpty(scope) && !MavenConstants.SCOPE_COMPILE.equals(scope)) {
            myToolTip.append(scope).append(" ");
        }
        if (myArtifactNode.getState() == MavenArtifactState.CONFLICT) {
            myToolTip.append("omitted for conflict");
            if (myArtifactNode.getRelatedArtifact() != null) {
                myToolTip.append(" with ").append(myArtifactNode.getRelatedArtifact().getVersion());
            }
        }
        if (myArtifactNode.getState() == MavenArtifactState.DUPLICATE) {
            myToolTip.append("omitted for duplicate");
        }
        return myToolTip.toString().trim();
    }

    @Override
    protected void doUpdate() {
        setNameAndTooltip(getName(), null, getToolTip());
    }

    @Override
    protected void setNameAndTooltip(String name, @Nullable String tooltip, SimpleTextAttributes attributes) {
        final SimpleTextAttributes mergedAttributes;
        if (myArtifactNode.getState() == MavenArtifactState.CONFLICT || myArtifactNode.getState() == MavenArtifactState.DUPLICATE) {
            mergedAttributes = SimpleTextAttributes.merge(attributes, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
            mergedAttributes = attributes;
        }
        super.setNameAndTooltip(name, tooltip, mergedAttributes);
    }

    public void updateDependency() {
        setErrorLevel(myArtifact.isResolved() ? MavenProjectsStructure.ErrorLevel.NONE : MavenProjectsStructure.ErrorLevel.ERROR);
    }

    @Override
    @RequiredReadAction
    public Navigatable getNavigatable() {
        final MavenArtifactNode parent = myArtifactNode.getParent();
        final VirtualFile file;
        if (parent == null) {
            file = getMavenProject().getFile();
        }
        else {
            final MavenId id = parent.getArtifact().getMavenId();
            final MavenProject pr = myMavenProjectsStructure.getProjectsManager().findProject(id);
            file = pr == null ? MavenNavigationUtil.getArtifactFile(getProject(), id) : pr.getFile();
        }
        return file == null ? null : MavenNavigationUtil.createNavigatableForDependency(getProject(), file, getArtifact());
    }

    @Override
    public boolean isVisible() {
        // show regardless absence of children
        return getDisplayKind() != MavenProjectsStructure.DisplayKind.NEVER;
    }
}
