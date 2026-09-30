package org.jetbrains.idea.maven.execution;

import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunnerParametersSettingEditor extends SettingsEditor<MavenRunConfiguration> {
    private final MavenRunnerParametersPanel myPanel;

    @RequiredUIAccess
    public MavenRunnerParametersSettingEditor(Project project) {
        myPanel = new MavenRunnerParametersPanel(project);
    }

    @Override
    @RequiredUIAccess
    protected void resetEditorFrom(MavenRunConfiguration runConfiguration) {
        myPanel.getData(runConfiguration.getRunnerParameters());
    }

    @Override
    @RequiredUIAccess
    protected void applyEditorTo(MavenRunConfiguration runConfiguration) throws ConfigurationException {
        myPanel.setData(runConfiguration.getRunnerParameters());
    }

    @Override
    @RequiredUIAccess
    protected Component createUIComponent() {
        return myPanel.getComponent();
    }

    @Override
    protected void disposeEditor() {
        myPanel.disposeUIResources();
    }
}
