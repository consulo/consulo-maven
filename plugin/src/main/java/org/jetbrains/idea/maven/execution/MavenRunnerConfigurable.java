package org.jetbrains.idea.maven.execution;

import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;

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

    public boolean isModified() {
        MavenRunnerSettings s = new MavenRunnerSettings();
        apply(s);
        return !s.equals(getState());
    }

    public void apply() throws ConfigurationException {
        apply(getState());
    }

    public void reset() {
        reset(getState());
    }

    @Nls
    public String getDisplayName() {
        return RunnerBundle.message("maven.tab.runner");
    }

    @Nullable
    public String getHelpTopic() {
        return "reference.settings.project.maven.runner";
    }

    @Nonnull
    public String getId() {
        //noinspection ConstantConditions
        return getHelpTopic();
    }

    public Runnable enableSearch(String option) {
        return null;
    }

    public void disposeUIResources() {
    }
}
