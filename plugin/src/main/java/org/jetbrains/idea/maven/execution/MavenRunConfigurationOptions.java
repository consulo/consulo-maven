/*
 * Copyright 2013-2026 consulo.io
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

import com.dslplatform.json.CompiledJson;
import consulo.execution.configuration.RunConfigurationOptions;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.MapAnnotation;
import consulo.util.xml.serializer.annotation.Tag;
import org.jetbrains.idea.maven.project.MavenGeneralSettingsOptions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * State of a {@link MavenRunConfiguration}: the goals to run and, when set, the settings used instead of the project ones.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@CompiledJson
public class MavenRunConfigurationOptions extends RunConfigurationOptions {
    private @Nullable String myWorkingDirectory;
    private List<String> myGoals = new ArrayList<>();
    private Map<String, Boolean> myProfiles = new LinkedHashMap<>();
    private boolean myResolveToWorkspace;
    private @Nullable MavenGeneralSettingsOptions myGeneralSettings;
    private @Nullable MavenRunnerSettingsOptions myRunnerSettings;

    @Attribute("workingDirectory")
    public @Nullable String getWorkingDirectory() {
        return myWorkingDirectory;
    }

    public void setWorkingDirectory(@Nullable String workingDirectory) {
        myWorkingDirectory = StringUtil.nullize(workingDirectory, true);
    }

    @Tag("goals")
    @AbstractCollection(surroundWithTag = false, elementTag = "goal", elementValueAttribute = "value")
    public List<String> getGoals() {
        return myGoals;
    }

    public void setGoals(@Nullable List<String> goals) {
        myGoals = goals == null ? new ArrayList<>() : new ArrayList<>(goals);
    }

    /**
     * Profile name to {@code true} for an enabled profile, {@code false} for a disabled one.
     */
    @Tag("profiles")
    @MapAnnotation(
        surroundWithTag = false,
        entryTagName = "profile",
        keyAttributeName = "name",
        valueAttributeName = "enabled",
        surroundKeyWithTag = false,
        surroundValueWithTag = false,
        sortBeforeSave = false
    )
    public Map<String, Boolean> getProfiles() {
        return myProfiles;
    }

    public void setProfiles(@Nullable Map<String, Boolean> profiles) {
        myProfiles = profiles == null ? new LinkedHashMap<>() : new LinkedHashMap<>(profiles);
    }

    @Attribute("resolveToWorkspace")
    public boolean isResolveToWorkspace() {
        return myResolveToWorkspace;
    }

    public void setResolveToWorkspace(boolean resolveToWorkspace) {
        myResolveToWorkspace = resolveToWorkspace;
    }

    /**
     * The general settings used instead of the project ones, {@code null} for the project settings.
     */
    @Tag("generalSettings")
    public @Nullable MavenGeneralSettingsOptions getGeneralSettings() {
        return myGeneralSettings;
    }

    public void setGeneralSettings(@Nullable MavenGeneralSettingsOptions generalSettings) {
        myGeneralSettings = generalSettings;
    }

    /**
     * The runner settings used instead of the project ones, {@code null} for the project settings.
     */
    @Tag("runnerSettings")
    public @Nullable MavenRunnerSettingsOptions getRunnerSettings() {
        return myRunnerSettings;
    }

    public void setRunnerSettings(@Nullable MavenRunnerSettingsOptions runnerSettings) {
        myRunnerSettings = runnerSettings;
    }
}
