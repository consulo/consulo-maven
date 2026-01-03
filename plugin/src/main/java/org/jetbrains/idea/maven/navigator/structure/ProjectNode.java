package org.jetbrains.idea.maven.navigator.structure;

import consulo.maven.icon.MavenIconGroup;
import consulo.maven.rt.server.common.model.MavenProjectProblem;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProjectNode extends GroupNode {
    private final MavenProject myMavenProject;
    private final LifecycleNode myLifecycleNode;
    private final PluginsNode myPluginsNode;
    private final RepositoriesNode myRepositoriesNode;
    private final DependenciesNode myDependenciesNode;
    private final ModulesNode myModulesNode;
    private final RunConfigurationsNode myRunConfigurationsNode;

    private String myTooltipCache;

    public ProjectNode(MavenProjectsStructure mavenProjectsStructure, @Nonnull MavenProject mavenProject) {
        super(mavenProjectsStructure, null);
        myMavenProject = mavenProject;

        myLifecycleNode = new LifecycleNode(mavenProjectsStructure, this);
        myPluginsNode = new PluginsNode(mavenProjectsStructure, this);
        myRepositoriesNode = new RepositoriesNode(mavenProjectsStructure, this);
        myDependenciesNode = new DependenciesNode(mavenProjectsStructure, this, mavenProject);
        myModulesNode = new ModulesNode(mavenProjectsStructure, this);
        myRunConfigurationsNode = new RunConfigurationsNode(mavenProjectsStructure, this);

        setIcon(MavenIconGroup.modulesclosed());
        updateProject();
    }

    public MavenProject getMavenProject() {
        return myMavenProject;
    }

    public ProjectsGroupNode getGroup() {
        return (ProjectsGroupNode) super.getParent();
    }

    @Override
    public boolean isVisible() {
        if (!myMavenProjectsStructure.getProjectsNavigator().getShowIgnored() && myMavenProjectsStructure.getProjectsManager().isIgnored(myMavenProject)) {
            return false;
        }
        return super.isVisible();
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
        var children =
            new CopyOnWriteArrayList<MavenSimpleNode>(List.of(myLifecycleNode, myPluginsNode, myRunConfigurationsNode, myDependenciesNode));
        if (isRoot()) {
            children.add(myRepositoriesNode);
        }
        children.addAll(super.doGetChildren());
        return children;
    }

    private boolean isRoot() {
        return myMavenProjectsStructure.getProjectsManager().findAggregator(myMavenProject) == null;
    }

    public ModulesNode getModulesNode() {
        return myModulesNode;
    }

    public void updateProject() {
        setErrorLevel(myMavenProject.getProblems().isEmpty() ? MavenProjectsStructure.ErrorLevel.NONE : MavenProjectsStructure.ErrorLevel.ERROR);
        myLifecycleNode.updateGoalsList();
        myPluginsNode.updatePlugins(myMavenProject);

        if (isRoot()) {
            myRepositoriesNode.updateRepositories(myMavenProjectsStructure.getProject());
        }

        if (myMavenProjectsStructure.isShown(DependencyNode.class)) {
            myDependenciesNode.updateDependencies();
        }

        myRunConfigurationsNode.updateRunConfigurations(myMavenProject);

        myTooltipCache = makeDescription();

        myMavenProjectsStructure.updateFrom(getParent());
    }

    public void updateIgnored() {
        getGroup().childrenChanged();
    }

    public void updateGoals() {
        myMavenProjectsStructure.updateFrom(myLifecycleNode);
        myMavenProjectsStructure.updateFrom(myPluginsNode);
    }

    public void updateRunConfigurations() {
        myRunConfigurationsNode.updateRunConfigurations(myMavenProject);
        myMavenProjectsStructure.updateFrom(myRunConfigurationsNode);
    }

    @Override
    public String getName() {
        if (myMavenProjectsStructure.getProjectsNavigator().getAlwaysShowArtifactId()) {
            return myMavenProject.getMavenId().getArtifactId();
        }
        else {
            return myMavenProject.getDisplayName();
        }
    }

    @Override
    protected void doUpdate() {
        String hint = null;

        if (!myMavenProjectsStructure.getProjectsNavigator().getGroupModules()
            && myMavenProjectsStructure.getProjectsManager().findAggregator(myMavenProject) == null
            && myMavenProjectsStructure.getProjectsManager().getProjects().size() > myMavenProjectsStructure.getProjectsManager().getRootProjects().size()) {
            hint = "root";
        }

        setNameAndTooltip(getName(), myTooltipCache, hint);
    }

    @Override
    protected SimpleTextAttributes getPlainAttributes() {
        if (myMavenProjectsStructure.getProjectsManager().isIgnored(myMavenProject)) {
            return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY);
        }
        return super.getPlainAttributes();
    }

    private String makeDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append("<html>" + "<table>" + "<tr>" + "<td nowrap>" + "<table>" + "<tr>" + "<td nowrap>Project:</td>" + "<td nowrap>")
            .append(myMavenProject.getMavenId())
            .append("</td>" + "</tr>" + "<tr>" + "<td nowrap>Location:</td>" + "<td nowrap>")
            .append(myMavenProject.getPath())
            .append("</td>" + "</tr>" + "</table>" + "</td>" + "</tr>");

        appendProblems(desc);

        desc.append("</table></html>");

        return desc.toString();
    }

    private void appendProblems(StringBuilder desc) {
        List<MavenProjectProblem> problems = myMavenProject.getProblems();
        if (problems.isEmpty()) {
            return;
        }

        desc.append("<tr>" + "<td nowrap>" + "<table>");

        boolean first = true;
        for (MavenProjectProblem each : problems) {
            desc.append("<tr>");
            if (first) {
                desc.append("<td nowrap valign=top>")
                    .append(MavenUtil.formatHtmlImage(PlatformIconGroup.generalError()))
                    .append("</td>");
                desc.append("<td nowrap valign=top>Problems:</td>");
                first = false;
            }
            else {
                desc.append("<td nowrap colspan=2></td>");
            }
            desc.append("<td nowrap valign=top>").append(wrappedText(each)).append("</td>");
            desc.append("</tr>");
        }
        desc.append("</table>" + "</td>" + "</tr>");
    }

    private String wrappedText(MavenProjectProblem each) {
        String description = ObjectUtil.chooseNotNull(each.getDescription(), each.getPath());
        if (description == null) {
            return "";
        }

        String text = StringUtil.replace(description, new String[]{
            "<",
            ">"
        }, new String[]{
            "&lt;",
            "&gt;"
        });
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            result.append(ch);

            if (count++ > 80) {
                if (ch == ' ') {
                    count = 0;
                    result.append("<br>");
                }
            }
        }
        return result.toString();
    }

    RepositoriesNode getRepositoriesNode() {
        return myRepositoriesNode;
    }

    @Override
    public VirtualFile getVirtualFile() {
        return myMavenProject.getFile();
    }

    @Override
    protected void setNameAndTooltip(String name, @Nullable String tooltip, SimpleTextAttributes attributes) {
        super.setNameAndTooltip(name, tooltip, attributes);
        if (myMavenProjectsStructure.getProjectsNavigator().getShowVersions()) {
            addColoredFragment(
                ":" + myMavenProject.getMavenId().getVersion(),
                new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY)
            );
        }
    }

    @Override
    @Nullable
    @NonNls
    public String getMenuId() {
        return "Maven.NavigatorProjectMenu";
    }
}
