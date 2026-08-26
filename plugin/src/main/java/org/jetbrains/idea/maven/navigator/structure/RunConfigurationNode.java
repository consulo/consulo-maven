package org.jetbrains.idea.maven.navigator.structure;

import consulo.dataContext.DataContext;
import consulo.execution.ProgramRunnerUtil;
import consulo.ui.event.details.InputDetails;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;

public class RunConfigurationNode extends MavenSimpleNode {
    private final RunnerAndConfigurationSettings mySettings;

    public RunConfigurationNode(MavenProjectsStructure structure, RunConfigurationsNode parent, RunnerAndConfigurationSettings settings) {
        super(structure, parent);
        mySettings = settings;
        setIcon(ProgramRunnerUtil.getConfigurationIcon(settings, false));
    }

    public RunnerAndConfigurationSettings getSettings() {
        return mySettings;
    }

    @Override
    public String getName() {
        return mySettings.getName();
    }

    @Override
    protected void doUpdate() {
        setNameAndTooltip(
            getName(),
            null,
            StringUtil.join(((MavenRunConfiguration)mySettings.getConfiguration()).getRunnerParameters().getGoals(), " ")
        );
    }

    @Nullable
    @Override
    public String getMenuId() {
        return "Maven.RunConfigurationMenu";
    }

    public void updateRunConfiguration() {
    }

    @Override
    public boolean handleDoubleClickOrEnter(DataContext context, @Nullable InputDetails inputDetails) {
        ProgramRunnerUtil.executeConfiguration(mySettings, DefaultRunExecutor.getRunExecutorInstance());
        return true;
    }
}
