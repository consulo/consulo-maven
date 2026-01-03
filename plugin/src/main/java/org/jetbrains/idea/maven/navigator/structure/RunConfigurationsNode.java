package org.jetbrains.idea.maven.navigator.structure;

import consulo.execution.RunManager;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.util.io.PathUtil;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.*;

import static org.jetbrains.idea.maven.project.ProjectBundle.message;

public class RunConfigurationsNode extends GroupNode {
    private final List<RunConfigurationNode> myChildren = new ArrayList<>();

    public RunConfigurationsNode(MavenProjectsStructure mavenProjectsStructure, ProjectNode parent) {
        super(mavenProjectsStructure, parent);
        setIcon(PlatformIconGroup.actionsExecute());
    }

    @Override
    public String getName() {
        return message("view.node.run.configurations");
    }

    @Override
    protected List<? extends MavenSimpleNode> doGetChildren() {
        return myChildren;
    }

    public void updateRunConfigurations(MavenProject mavenProject) {
        boolean childChanged = false;

        Set<RunnerAndConfigurationSettings> settings =
            new HashSet<>(RunManager.getInstance(myMavenProjectsStructure.getProject()).getConfigurationSettingsList(MavenRunConfigurationType.getInstance()));

        for (Iterator<RunConfigurationNode> itr = myChildren.iterator(); itr.hasNext(); ) {
            RunConfigurationNode node = itr.next();

            if (settings.remove(node.getSettings())) {
                node.updateRunConfiguration();
            }
            else {
                itr.remove();
                childChanged = true;
            }
        }

        String directory = PathUtil.getCanonicalPath(mavenProject.getDirectory());

        int oldSize = myChildren.size();

        for (RunnerAndConfigurationSettings cfg : settings) {
            MavenRunConfiguration mavenRunConfiguration = (MavenRunConfiguration) cfg.getConfiguration();

            if (directory.equals(PathUtil.getCanonicalPath(mavenRunConfiguration.getRunnerParameters().getWorkingDirPath()))) {
                myChildren.add(new RunConfigurationNode(myMavenProjectsStructure, this, cfg));
            }
        }

        if (oldSize != myChildren.size()) {
            childChanged = true;
            sort(myChildren);
        }

        if (childChanged) {
            childrenChanged();
        }
    }
}
