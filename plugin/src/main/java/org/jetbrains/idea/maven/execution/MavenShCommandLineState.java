/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
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

import consulo.application.platform.PlatformTransfer;
import consulo.container.plugin.PluginManager;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.maven.util.MavenJdkUtil;
import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Runs the goals with the {@code mvn} script of the maven distribution through a shell of the machine of the project
 * (JetBrains: {@code MavenShCommandLineState}) - the distribution and the event listener are transferred to that machine.
 */
public class MavenShCommandLineState extends MavenCommandLineState {
    private final MavenRunConfiguration myConfiguration;

    public MavenShCommandLineState(ExecutionEnvironment environment, MavenRunConfiguration configuration) {
        super(environment, configuration);
        myConfiguration = configuration;
    }

    @Override
    public ProcessHandler startMavenProcess() throws ExecutionException {
        Project project = myConfiguration.getProject();
        Platform platform = project.getPlatform();
        boolean windows = platform.os().isWindows();

        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.withPlatform(platform);
        commandLine.withCharset(StandardCharsets.UTF_8);
        commandLine.withWorkingDirectory(platform.fs().getPath(myConfiguration.getRunnerParameters().getWorkingDirPath()));
        commandLine.withExePath(windows ? "cmd.exe" : "/bin/sh");
        if (windows) {
            commandLine.addParameter("/c");
        }
        commandLine.addParameters(collectArguments(platform).getList());
        commandLine.withEnvironment(getEnv(platform));

        ProcessHandler handler = ProcessHandlerBuilder.create(commandLine)
            .colored()
            .killable()
            .shouldDestroyProcessRecursively(true)
            .build();
        setupProcessHandler(handler);
        return handler;
    }

    private MavenRunnerSettings getMavenRunnerSettings() {
        MavenRunnerSettings settings = myConfiguration.getRunnerSettings();
        return settings != null ? settings : MavenRunner.getInstance(myConfiguration.getProject()).getState();
    }

    private MavenGeneralSettings getMavenGeneralSettings() {
        MavenGeneralSettings settings = myConfiguration.getGeneralSettings();
        return settings != null ? settings : MavenProjectsManager.getInstance(myConfiguration.getProject()).getGeneralSettings();
    }

    private ParametersList collectArguments(Platform platform) throws ExecutionException {
        ParametersList args = new ParametersList();
        args.add(getScriptPath(platform));
        addIdeaParameters(args, platform);
        addSettingParameters(args);

        args.addAll(myConfiguration.getRunnerParameters().getGoals());
        return args;
    }

    private void addSettingParameters(ParametersList args) {
        MavenRunnerParameters parameters = myConfiguration.getRunnerParameters();
        MavenRunnerSettings runnerSettings = getMavenRunnerSettings();
        MavenGeneralSettings generalSettings = getMavenGeneralSettings();

        String encodeProfiles = MavenExternalParameters.encodeProfiles(parameters.getProfilesMap());
        if (!encodeProfiles.isEmpty()) {
            args.addAll("-P", encodeProfiles);
        }
        for (Map.Entry<String, String> entry : runnerSettings.getMavenProperties().entrySet()) {
            if (!entry.getKey().isEmpty()) {
                args.addProperty(entry.getKey(), entry.getValue());
            }
        }

        if (runnerSettings.isSkipTests()) {
            args.addProperty("skipTests", "true");
        }

        if (generalSettings.isWorkOffline()) {
            args.add("--offline");
        }
        if (generalSettings.getOutputLevel() == MavenExecutionOptions.LoggingLevel.DEBUG) {
            args.add("--debug");
        }
        if (generalSettings.isNonRecursive()) {
            args.add("--non-recursive");
        }
        if (generalSettings.isPrintErrorStackTraces()) {
            args.add("--errors");
        }
        if (generalSettings.isAlwaysUpdateSnapshots()) {
            args.add("--update-snapshots");
        }
        String threads = generalSettings.getThreads();
        if (!StringUtil.isEmptyOrSpaces(threads)) {
            args.addAll("-T", threads);
        }
        // the settings and the repository of the user of the machine - unless set explicitly, as paths of that machine
        if (!StringUtil.isEmptyOrSpaces(generalSettings.getUserSettingsFile())) {
            args.addAll("-s", generalSettings.getUserSettingsFile());
        }
        if (!StringUtil.isEmptyOrSpaces(generalSettings.getLocalRepository())) {
            args.addProperty("maven.repo.local", generalSettings.getLocalRepository());
        }
    }

    private void addIdeaParameters(ParametersList args, Platform platform) throws ExecutionException {
        addMavenEventListener(args, platform);
        args.addProperty("jansi.passthrough", "true");
        args.addProperty("style.color", "always");
    }

    private void addMavenEventListener(ParametersList args, Platform platform) throws ExecutionException {
        File listener = new File(PluginManager.getPluginPath(MavenExternalParameters.class), "maven-event-listener.jar");
        if (!listener.isFile()) {
            throw new IllegalStateException(listener + " does not exist");
        }
        args.addProperty("maven.ext.class.path", transfer(listener.toPath(), platform).toString());
    }

    /**
     * The {@code mvn} script of the distribution of the settings, transferred to the machine.
     */
    private String getScriptPath(Platform platform) throws ExecutionException {
        File mavenHome = MavenUtil.resolveMavenHomeDirectory(getMavenGeneralSettings().getMavenBundleName());
        if (mavenHome == null || !mavenHome.isDirectory()) {
            throw new ExecutionException(MavenRunnerLocalize.externalMavenHomeNoDefault().get());
        }
        Path remoteHome = transfer(mavenHome.toPath(), platform);
        return remoteHome.resolve("bin").resolve(platform.os().isWindows() ? "mvn.cmd" : "mvn").toString();
    }

    private static Path transfer(Path path, Platform platform) throws ExecutionException {
        try {
            return PlatformTransfer.getInstance().transfer(path.toRealPath(), platform);
        }
        catch (IOException e) {
            throw new ExecutionException("Cannot transfer " + path + " to " + platform.getName() + ": " + e.getMessage(), e);
        }
    }

    private Map<String, String> getEnv(Platform platform) throws ExecutionException {
        MavenRunnerSettings runnerSettings = getMavenRunnerSettings();

        Map<String, String> map = new HashMap<>();
        map.put("JAVA_HOME", getJavaHome(platform));

        StringBuilder optsBuilder = new StringBuilder(StringUtil.notNullize(platform.os().getEnvironmentVariable("MAVEN_OPTS")));
        String vmOptions = runnerSettings.getVmOptions();
        if (!StringUtil.isEmptyOrSpaces(vmOptions)) {
            if (optsBuilder.length() > 0) {
                optsBuilder.append(' ');
            }
            optsBuilder.append(vmOptions);
        }
        // the output is read as UTF-8
        if (optsBuilder.indexOf("-Dfile.encoding") < 0) {
            if (optsBuilder.length() > 0) {
                optsBuilder.append(' ');
            }
            optsBuilder.append("-Dfile.encoding=UTF-8");
        }
        map.put("MAVEN_OPTS", optsBuilder.toString());
        map.putAll(runnerSettings.getEnvironmentProperties());
        return map;
    }

    /**
     * The home of the bundle of the runner when it is of the machine of the project, else a bundle of that machine.
     */
    private String getJavaHome(Platform platform) throws ExecutionException {
        String targetId = myConfiguration.getProject().getPlatformTargetId();
        String jreName = getMavenRunnerSettings().getJreName();
        Sdk sdk = jreName == null ? null : SdkTable.getInstance().findSdk(jreName);
        if (sdk == null || !Objects.equals(sdk.getPlatformTargetId(), targetId)) {
            String mavenVersion = MavenServerManager.getInstance().getCurrentMavenVersion();
            sdk = MavenJdkUtil.findSdkOfLevel(MavenJdkUtil.getDefaultRunLevel(mavenVersion), null, targetId);
        }
        Path home = sdk == null ? null : sdk.getHomeNioPath();
        if (home == null || !Files.isDirectory(home)) {
            throw new ExecutionException(MavenRunnerLocalize.mavenJavaNotResolved().get());
        }
        return home.toString();
    }
}
