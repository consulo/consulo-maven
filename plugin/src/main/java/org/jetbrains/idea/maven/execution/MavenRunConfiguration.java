/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.execution;

import com.intellij.java.execution.configurations.RemoteConnectionCreator;
import consulo.annotation.access.RequiredReadAction;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.LocatableConfigurationBase;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.java.debugger.impl.GenericDebugRunnerConfiguration;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.XmlSerializer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenGeneralSettingsFragment;
import org.jetbrains.idea.maven.project.MavenGeneralSettingsOptions;

import java.util.List;

public class MavenRunConfiguration extends LocatableConfigurationBase implements GenericDebugRunnerConfiguration {
    protected MavenRunConfiguration(Project project, ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @Override
    public MavenRunConfiguration clone() {
        return (MavenRunConfiguration) super.clone();
    }

    @Override
    protected Class<MavenRunConfigurationOptions> getOptionsClass() {
        return MavenRunConfigurationOptions.class;
    }

    @Override
    public MavenRunConfigurationOptions getOptions() {
        return (MavenRunConfigurationOptions) super.getOptions();
    }

    @Override
    protected void convertLegacyState(LegacyRunConfigurationConverter converter) {
        super.convertLegacyState(converter);

        MavenRunConfigurationOptions options = new MavenRunConfigurationOptions();

        Element mavenSettingsElement = converter.getElement().getChild(MavenRunConfigurationLegacyState.TAG);
        MavenRunConfigurationLegacyState old = mavenSettingsElement == null
            ? null
            : XmlSerializer.deserialize(mavenSettingsElement, MavenRunConfigurationLegacyState.class);
        if (old != null) {
            MavenRunnerParameters parameters = old.myRunnerParameters;
            if (parameters != null) {
                parameters.fixAfterLoadingFromOldFormat();
                setRunnerParameters(options, parameters);
            }

            MavenGeneralSettings generalSettings = old.myGeneralSettings;
            options.setGeneralSettings(generalSettings == null ? null : generalSettings.toOptions());

            MavenRunnerSettings runnerSettings = old.myRunnerSettings;
            options.setRunnerSettings(runnerSettings == null ? null : MavenRunnerSettingsOptions.of(runnerSettings));
        }

        converter.setOptions(options);
    }

    @Override
    public List<RunConfigurationFragment<?>> createFragments() {
        return List.of(
            new MavenRunnerParametersFragment(),
            new MavenProfilesFragment(),
            new MavenResolveToWorkspaceFragment(),
            new MavenGeneralSettingsFragment(),
            new MavenRunnerSettingsFragment()
        );
    }

    @RequiredReadAction
    public OwnJavaParameters createJavaParameters(@Nullable Project project) throws ExecutionException {
        return MavenExternalParameters.createJavaParameters(
            project,
            getRunnerParameters(),
            getGeneralSettings(),
            getRunnerSettings(),
            this
        );
    }

    @Override
    public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment env) throws ExecutionException {
        return new MavenCommandLineState(env, this);
    }

    @Override
    @Nonnull
    public Module[] getModules() {
        return Module.EMPTY_ARRAY;
    }

    /**
     * A copy of the general settings used instead of the project ones, {@code null} for the project settings.
     */
    @Nullable
    public MavenGeneralSettings getGeneralSettings() {
        MavenGeneralSettingsOptions options = getOptions().getGeneralSettings();
        return options == null ? null : MavenGeneralSettings.fromOptions(options);
    }

    public void setGeneralSettings(@Nullable MavenGeneralSettings settings) {
        getOptions().setGeneralSettings(settings == null ? null : settings.toOptions());
    }

    /**
     * A copy of the runner settings used instead of the project ones, {@code null} for the project settings.
     */
    @Nullable
    public MavenRunnerSettings getRunnerSettings() {
        MavenRunnerSettingsOptions options = getOptions().getRunnerSettings();
        return options == null ? null : options.toSettings();
    }

    public void setRunnerSettings(@Nullable MavenRunnerSettings settings) {
        getOptions().setRunnerSettings(settings == null ? null : MavenRunnerSettingsOptions.of(settings));
    }

    /**
     * A copy of the parameters; changes are kept by {@link #setRunnerParameters(MavenRunnerParameters)}.
     */
    public MavenRunnerParameters getRunnerParameters() {
        MavenRunConfigurationOptions options = getOptions();
        MavenRunnerParameters parameters = new MavenRunnerParameters(
            StringUtil.notNullize(options.getWorkingDirectory()),
            true,
            options.getGoals(),
            options.getProfiles()
        );
        parameters.setResolveToWorkspace(options.isResolveToWorkspace());
        return parameters;
    }

    public void setRunnerParameters(MavenRunnerParameters p) {
        setRunnerParameters(getOptions(), p);
    }

    private static void setRunnerParameters(MavenRunConfigurationOptions options, MavenRunnerParameters p) {
        options.setWorkingDirectory(p.getWorkingDirPath());
        options.setGoals(p.getGoals());
        options.setProfiles(p.getProfilesMap());
        options.setResolveToWorkspace(p.isResolveToWorkspace());
    }

    @Override
    public String suggestedName() {
        return MavenRunConfigurationType.generateName(getProject(), getRunnerParameters());
    }

    public RemoteConnectionCreator createRemoteConnectionCreator(OwnJavaParameters javaParameters) {
        return new MavenExtRemoteConnectionCreator(javaParameters, this);
    }
}
