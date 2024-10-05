package org.jetbrains.idea.maven.execution;

import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenRunnerConfigurable extends MavenRunnerPanel implements SearchableConfigurable, Configurable.NoScroll {

    public MavenRunnerConfigurable(@Nonnull Project p, boolean isRunConfiguration) {
        super(p, isRunConfiguration);
    }

    @Nullable
    protected abstract MavenRunnerSettings getState();

    @Override
    @RequiredUIAccess
    public boolean isModified() {
        MavenRunnerSettings s = new MavenRunnerSettings();
        apply(s);
        return !s.equals(getState());
    }

    @Override
    @RequiredUIAccess
    public void apply() throws ConfigurationException {
        apply(getState());
    }

    @Override
    @RequiredUIAccess
    public void reset() {
        reset(getState());
    }

    @Override
    public String getDisplayName() {
        return MavenRunnerLocalize.mavenTabRunner().get();
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return "reference.settings.project.maven.runner";
    }

    @Nonnull
    @Override
    public String getId() {
        //noinspection ConstantConditions
        return getHelpTopic();
    }

    @Override
    public Runnable enableSearch(String option) {
        return null;
    }

    @Override
    @RequiredUIAccess
    public void disposeUIResources() {
    }
}
