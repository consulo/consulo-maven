package org.jetbrains.idea.maven.navigator.structure;

import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.project.MavenProject;

import java.awt.*;

public abstract class GoalNode extends MavenSimpleNode {
    private final MavenProject myMavenProject;
    private final String myGoal;
    private final String myDisplayName;

    public GoalNode(MavenProjectsStructure mavenProjectsStructure, GoalsGroupNode parent, String goal, String displayName) {
        super(mavenProjectsStructure, parent);
        myMavenProject = findParent(ProjectNode.class).getMavenProject();
        myGoal = goal;
        myDisplayName = displayName;
        setIcon(PlatformIconGroup.nodesTarget());
    }

    public MavenProject getMavenProject() {
        return myMavenProject;
    }

    public String getProjectPath() {
        return myMavenProject.getPath();
    }

    public String getGoal() {
        return myGoal;
    }

    @Override
    public String getName() {
        return myDisplayName;
    }

    @Override
    protected void doUpdate() {
        String s1 = StringUtil.nullize(myMavenProjectsStructure.getShortcutsManager().getDescription(myMavenProject, myGoal));
        String s2 = StringUtil.nullize(myMavenProjectsStructure.getTasksManager().getDescription(myMavenProject, myGoal));

        String hint;
        if (s1 == null) {
            hint = s2;
        }
        else if (s2 == null) {
            hint = s1;
        }
        else {
            hint = s1 + ", " + s2;
        }

        setNameAndTooltip(getName(), null, hint);
    }

    @Override
    protected SimpleTextAttributes getPlainAttributes() {
        SimpleTextAttributes original = super.getPlainAttributes();

        int style = original.getStyle();
        Color color = original.getFgColor();
        boolean custom = false;

        if ("test".equals(myGoal) && MavenRunner.getInstance(myMavenProjectsStructure.getProject()).getSettings().isSkipTests()) {
            color = SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor();
            style |= SimpleTextAttributes.STYLE_STRIKEOUT;
            custom = true;
        }
        if (myGoal.equals(myMavenProject.getDefaultGoal())) {
            style |= SimpleTextAttributes.STYLE_BOLD;
            custom = true;
        }
        if (custom) {
            return original.derive(style, color, null, null);
        }
        return original;
    }

    @Override
    @Nullable
    @NonNls
    protected String getActionId() {
        return "Maven.RunBuild";
    }

    @Override
    @Nullable
    @NonNls
    public String getMenuId() {
        return "Maven.BuildMenu";
    }
}
