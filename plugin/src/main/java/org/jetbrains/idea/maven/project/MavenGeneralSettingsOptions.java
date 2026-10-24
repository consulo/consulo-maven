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
package org.jetbrains.idea.maven.project;

import com.dslplatform.json.CompiledJson;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.Tag;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jspecify.annotations.Nullable;

/**
 * The general maven settings a run configuration uses instead of the project ones.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@CompiledJson
@Tag("settings")
public class MavenGeneralSettingsOptions {
    private boolean myWorkOffline;
    private @Nullable String myMavenBundleName;
    private @Nullable String myUserSettingsFile;
    private @Nullable String myLocalRepository;
    private boolean myPrintErrorStackTraces;
    private boolean myUsePluginRegistry;
    private boolean myNonRecursive;
    private boolean myAlwaysUpdateSnapshots;
    private @Nullable String myThreads;
    private MaveOverrideCompilerPolicy myOverrideCompilePolicy = MaveOverrideCompilerPolicy.BY_COMPILE;
    private MavenExecutionOptions.LoggingLevel myOutputLevel = MavenExecutionOptions.LoggingLevel.INFO;
    private MavenExecutionOptions.ChecksumPolicy myChecksumPolicy = MavenExecutionOptions.ChecksumPolicy.NOT_SET;
    private MavenExecutionOptions.FailureMode myFailureBehavior = MavenExecutionOptions.FailureMode.NOT_SET;
    private MavenExecutionOptions.PluginUpdatePolicy myPluginUpdatePolicy = MavenExecutionOptions.PluginUpdatePolicy.DEFAULT;

    @Attribute("workOffline")
    public boolean isWorkOffline() {
        return myWorkOffline;
    }

    public void setWorkOffline(boolean workOffline) {
        myWorkOffline = workOffline;
    }

    @Attribute("mavenBundleName")
    public @Nullable String getMavenBundleName() {
        return myMavenBundleName;
    }

    public void setMavenBundleName(@Nullable String mavenBundleName) {
        myMavenBundleName = StringUtil.nullize(mavenBundleName, true);
    }

    @Attribute("userSettingsFile")
    public @Nullable String getUserSettingsFile() {
        return myUserSettingsFile;
    }

    public void setUserSettingsFile(@Nullable String userSettingsFile) {
        myUserSettingsFile = StringUtil.nullize(userSettingsFile, true);
    }

    @Attribute("localRepository")
    public @Nullable String getLocalRepository() {
        return myLocalRepository;
    }

    public void setLocalRepository(@Nullable String localRepository) {
        myLocalRepository = StringUtil.nullize(localRepository, true);
    }

    @Attribute("printErrorStackTraces")
    public boolean isPrintErrorStackTraces() {
        return myPrintErrorStackTraces;
    }

    public void setPrintErrorStackTraces(boolean printErrorStackTraces) {
        myPrintErrorStackTraces = printErrorStackTraces;
    }

    @Attribute("usePluginRegistry")
    public boolean isUsePluginRegistry() {
        return myUsePluginRegistry;
    }

    public void setUsePluginRegistry(boolean usePluginRegistry) {
        myUsePluginRegistry = usePluginRegistry;
    }

    @Attribute("nonRecursive")
    public boolean isNonRecursive() {
        return myNonRecursive;
    }

    public void setNonRecursive(boolean nonRecursive) {
        myNonRecursive = nonRecursive;
    }

    @Attribute("alwaysUpdateSnapshots")
    public boolean isAlwaysUpdateSnapshots() {
        return myAlwaysUpdateSnapshots;
    }

    public void setAlwaysUpdateSnapshots(boolean alwaysUpdateSnapshots) {
        myAlwaysUpdateSnapshots = alwaysUpdateSnapshots;
    }

    @Attribute("threads")
    public @Nullable String getThreads() {
        return myThreads;
    }

    public void setThreads(@Nullable String threads) {
        myThreads = StringUtil.nullize(threads, true);
    }

    @Attribute("overrideCompilePolicy")
    public MaveOverrideCompilerPolicy getOverrideCompilePolicy() {
        return myOverrideCompilePolicy;
    }

    public void setOverrideCompilePolicy(@Nullable MaveOverrideCompilerPolicy overrideCompilePolicy) {
        myOverrideCompilePolicy = overrideCompilePolicy == null ? MaveOverrideCompilerPolicy.BY_COMPILE : overrideCompilePolicy;
    }

    @Attribute("outputLevel")
    public MavenExecutionOptions.LoggingLevel getOutputLevel() {
        return myOutputLevel;
    }

    public void setOutputLevel(MavenExecutionOptions.@Nullable LoggingLevel outputLevel) {
        myOutputLevel = outputLevel == null ? MavenExecutionOptions.LoggingLevel.INFO : outputLevel;
    }

    @Attribute("checksumPolicy")
    public MavenExecutionOptions.ChecksumPolicy getChecksumPolicy() {
        return myChecksumPolicy;
    }

    public void setChecksumPolicy(MavenExecutionOptions.@Nullable ChecksumPolicy checksumPolicy) {
        myChecksumPolicy = checksumPolicy == null ? MavenExecutionOptions.ChecksumPolicy.NOT_SET : checksumPolicy;
    }

    @Attribute("failureBehavior")
    public MavenExecutionOptions.FailureMode getFailureBehavior() {
        return myFailureBehavior;
    }

    public void setFailureBehavior(MavenExecutionOptions.@Nullable FailureMode failureBehavior) {
        myFailureBehavior = failureBehavior == null ? MavenExecutionOptions.FailureMode.NOT_SET : failureBehavior;
    }

    @Attribute("pluginUpdatePolicy")
    public MavenExecutionOptions.PluginUpdatePolicy getPluginUpdatePolicy() {
        return myPluginUpdatePolicy;
    }

    public void setPluginUpdatePolicy(MavenExecutionOptions.@Nullable PluginUpdatePolicy pluginUpdatePolicy) {
        myPluginUpdatePolicy = pluginUpdatePolicy == null ? MavenExecutionOptions.PluginUpdatePolicy.DEFAULT : pluginUpdatePolicy;
    }
}
