package org.jetbrains.idea.maven.navigator.structure;

import consulo.execution.ProgramRunnerUtil;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.ui.ex.awt.tree.SimpleTree;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;

import java.awt.event.InputEvent;

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
    public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
        ProgramRunnerUtil.executeConfiguration(mySettings, DefaultRunExecutor.getRunExecutorInstance());
    }
}
