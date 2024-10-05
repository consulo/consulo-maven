package org.jetbrains.idea.maven.execution;

import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.ui.ex.awt.BorderLayoutPanel;
import consulo.ui.ex.awt.JBUI;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;
import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunnerParametersSettingEditor extends SettingsEditor<MavenRunConfiguration> {
    private final MavenRunnerParametersPanel myPanel;
    private BorderLayoutPanel myVerticalPanel;

    @RequiredUIAccess
    public MavenRunnerParametersSettingEditor(@Nonnull Project project) {
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

    @Nonnull
    @Override
    protected JComponent createEditor() {
        if (myVerticalPanel != null) {
            return myVerticalPanel;
        }
        myVerticalPanel = new BorderLayoutPanel();
        myVerticalPanel.setBorder(JBUI.Borders.empty(5, 0, 0, 0));
        myVerticalPanel.addToTop(myPanel.createComponent());
        return myVerticalPanel;
    }

    @Override
    protected void disposeEditor() {
        myPanel.disposeUIResources();
    }
}
