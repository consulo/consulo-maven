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
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.MapAnnotation;
import consulo.util.xml.serializer.annotation.Tag;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The maven runner settings a run configuration uses instead of the project ones.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@CompiledJson
@Tag("settings")
public class MavenRunnerSettingsOptions {
    private boolean myRunMavenInBackground = true;
    private @Nullable String myJreName;
    private @Nullable String myVmOptions;
    private boolean mySkipTests;
    private Map<String, String> myMavenProperties = new LinkedHashMap<>();
    private Map<String, String> myEnvironmentProperties = new LinkedHashMap<>();
    private boolean myPassParentEnv = true;

    public static MavenRunnerSettingsOptions of(MavenRunnerSettings settings) {
        MavenRunnerSettingsOptions options = new MavenRunnerSettingsOptions();
        options.setRunMavenInBackground(settings.isRunMavenInBackground());
        options.setJreName(settings.getJreName());
        options.setVmOptions(settings.getVmOptions());
        options.setSkipTests(settings.isSkipTests());
        options.setMavenProperties(settings.getMavenProperties());
        options.setEnvironmentProperties(settings.getEnvironmentProperties());
        options.setPassParentEnv(settings.isPassParentEnv());
        return options;
    }

    public MavenRunnerSettings toSettings() {
        MavenRunnerSettings settings = new MavenRunnerSettings();
        settings.setRunMavenInBackground(myRunMavenInBackground);
        settings.setJreName(myJreName);
        settings.setVmOptions(StringUtil.notNullize(myVmOptions));
        settings.setSkipTests(mySkipTests);
        settings.setMavenProperties(new LinkedHashMap<>(myMavenProperties));
        settings.setEnvironmentProperties(myEnvironmentProperties);
        settings.setPassParentEnv(myPassParentEnv);
        return settings;
    }

    @Attribute("runMavenInBackground")
    public boolean isRunMavenInBackground() {
        return myRunMavenInBackground;
    }

    public void setRunMavenInBackground(boolean runMavenInBackground) {
        myRunMavenInBackground = runMavenInBackground;
    }

    @Attribute("jreName")
    public @Nullable String getJreName() {
        return myJreName;
    }

    public void setJreName(@Nullable String jreName) {
        myJreName = StringUtil.nullize(jreName, true);
    }

    @Attribute("vmOptions")
    public @Nullable String getVmOptions() {
        return myVmOptions;
    }

    public void setVmOptions(@Nullable String vmOptions) {
        myVmOptions = StringUtil.nullize(vmOptions, true);
    }

    @Attribute("skipTests")
    public boolean isSkipTests() {
        return mySkipTests;
    }

    public void setSkipTests(boolean skipTests) {
        mySkipTests = skipTests;
    }

    @Tag("properties")
    @MapAnnotation(
        surroundWithTag = false,
        entryTagName = "property",
        keyAttributeName = "name",
        valueAttributeName = "value",
        surroundKeyWithTag = false,
        surroundValueWithTag = false,
        sortBeforeSave = false
    )
    public Map<String, String> getMavenProperties() {
        return myMavenProperties;
    }

    public void setMavenProperties(@Nullable Map<String, String> mavenProperties) {
        myMavenProperties = mavenProperties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(mavenProperties);
    }

    @Tag("envs")
    @MapAnnotation(
        surroundWithTag = false,
        entryTagName = "env",
        keyAttributeName = "name",
        valueAttributeName = "value",
        surroundKeyWithTag = false,
        surroundValueWithTag = false,
        sortBeforeSave = false
    )
    public Map<String, String> getEnvironmentProperties() {
        return myEnvironmentProperties;
    }

    public void setEnvironmentProperties(@Nullable Map<String, String> environmentProperties) {
        myEnvironmentProperties = environmentProperties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(environmentProperties);
    }

    @Attribute("passParentEnv")
    public boolean isPassParentEnv() {
        return myPassParentEnv;
    }

    public void setPassParentEnv(boolean passParentEnv) {
        myPassParentEnv = passParentEnv;
    }
}
