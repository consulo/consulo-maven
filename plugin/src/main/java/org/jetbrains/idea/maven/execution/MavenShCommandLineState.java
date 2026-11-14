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

import com.intellij.java.execution.configurations.RemoteConnection;
import consulo.application.platform.PlatformTransfer;
import consulo.container.plugin.PluginManager;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.maven.util.MavenJdkUtil;
import consulo.platform.ForwardedPort;
import consulo.platform.Platform;
import consulo.process.CommandLineUtil;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.project.Project;
import consulo.util.io.URLUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Runs the goals with the {@code mvn} script of the maven distribution through a shell of the machine of the project
 * - the distribution and the event listener are transferred to that machine.
 */
public class MavenShCommandLineState extends MavenCommandLineState {
    private final MavenRunConfiguration myConfiguration;
    // the agent of a debugged maven and its port forwarded to the IDE machine
    private @Nullable String myDebugAgent;
    private @Nullable ForwardedPort myDebugPort;

    public MavenShCommandLineState(ExecutionEnvironment environment, MavenRunConfiguration configuration) {
        super(environment, configuration);
        myConfiguration = configuration;
    }

    /**
     * The maven JVM listens on a port of the machine of the project - the debugger attaches through a forwarded port.
     */
    @Override
    public @Nullable RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
        Platform platform = myConfiguration.getProject().getPlatform();
        // a port of the machine of the project - free ports of this machine say nothing about it
        int port = 30000 + new Random().nextInt(20000);
        try {
            myDebugPort = platform.net().forward("127.0.0.1", port);
        }
        catch (IOException e) {
            MavenLog.LOG.warn("Cannot forward the debug port of maven: " + e.getMessage());
            return null;
        }
        myDebugAgent = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:" + port;
        return new RemoteConnection(true, myDebugPort.host(), String.valueOf(myDebugPort.port()), false);
    }

    @Override
    public boolean isPollConnection() {
        return true;
    }

    @Override
    public ProcessHandler startMavenProcess() throws ExecutionException {
        Project project = myConfiguration.getProject();
        Platform platform = project.getPlatform();
        boolean windows = platform.os().isWindows();

        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.withPlatform(platform);
        commandLine.withCharset(StandardCharsets.UTF_8);
        commandLine.withWorkingDirectory(getWorkingDirectory(platform));
        commandLine.withExePath(windows ? "cmd.exe" : "/bin/sh");
        if (windows) {
            // cmd.exe handles the quotes of its command line badly - the parameters go to a script
            commandLine.addParameter("/c");
            commandLine.addParameter(writeParamsToBat(collectArguments(platform), platform));
        }
        else {
            commandLine.addParameters(collectArguments(platform).getList());
        }
        commandLine.withEnvironment(getEnv(platform));

        ProcessHandler handler = ProcessHandlerBuilder.create(commandLine)
            .colored()
            .killable()
            .shouldDestroyProcessRecursively(true)
            .build();
        setupProcessHandler(handler);
        ForwardedPort debugPort = myDebugPort;
        if (debugPort != null) {
            handler.addProcessListener(new ProcessListener() {
                @Override
                public void processTerminated(ProcessEvent event) {
                    debugPort.close();
                }
            });
        }
        return handler;
    }

    /**
     * A {@code .bat} of the parameters on the machine of the project:
     * a {@code -D} value with spaces goes through an environment variable of the script.
     *
     * @return the path of the script on that machine
     */
    private static String writeParamsToBat(ParametersList params, Platform platform) throws ExecutionException {
        StringBuilder tmpData = new StringBuilder();
        Map<String, String> mapEnv = new LinkedHashMap<>();
        List<String> batParams = new ArrayList<>();

        int counter = 0;
        for (String param : params.getList()) {
            if (param.startsWith("-D") && param.contains(" ")) {
                int eqIndex = param.indexOf('=');
                if (eqIndex == -1 || eqIndex == param.length() - 1) {
                    batParams.add(param);
                }
                else {
                    String varName = "IDEA_MVN_TMP_" + ++counter;
                    String paramValue = param.substring(eqIndex + 1);
                    String paramName = param.substring(2, eqIndex);
                    mapEnv.put(varName, paramValue.replace("%", "%%"));
                    batParams.add("-D" + paramName + "=%" + varName + "%");
                }
            }
            else {
                batParams.add(param);
            }
        }

        mapEnv.forEach((name, value) -> tmpData.append("@set ").append(name).append("=").append(value).append("\r\n"));
        if (tmpData.length() > 0) {
            tmpData.append("\r\n");
        }
        tmpData.append("@").append(batParams.stream()
            .map(param -> CommandLineUtil.escapeParameterOnWindows(param, true))
            .collect(Collectors.joining(" ")));

        try {
            Path tmpBat = Files.createTempFile("mvn-idea-exec", ".bat");
            try {
                Files.writeString(tmpBat, tmpData.toString());
                return transfer(tmpBat, platform).toString();
            }
            finally {
                Files.deleteIfExists(tmpBat);
            }
        }
        catch (IOException e) {
            throw new ExecutionException("Cannot write the maven script: " + e.getMessage(), e);
        }
    }

    private MavenRunnerSettings getMavenRunnerSettings() {
        MavenRunnerSettings settings = myConfiguration.getRunnerSettings();
        return settings != null ? settings : MavenRunner.getInstance(myConfiguration.getProject()).getState();
    }

    private MavenGeneralSettings getMavenGeneralSettings() {
        MavenGeneralSettings settings = myConfiguration.getGeneralSettings();
        return settings != null ? settings : MavenProjectsManager.getInstance(myConfiguration.getProject()).getGeneralSettings();
    }

    /**
     * The working directory on the machine of the project - a configuration of an older version may hold the path of the
     * virtual file system ({@code <platform id>/path}).
     */
    private Path getWorkingDirectory(Platform platform) {
        String path = myConfiguration.getRunnerParameters().getWorkingDirPath();
        Path directory = platform.fs().getPath(path);
        if (!directory.isAbsolute()) {
            VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(StandardFileSystems.PLATFORM_PROTOCOL + URLUtil.SCHEME_SEPARATOR + path);
            if (file != null && file.isInLocalLikeFileSystem()) {
                return file.toNioPath();
            }
        }
        return directory;
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
        if (myDebugAgent != null) {
            optsBuilder.append(' ').append(myDebugAgent);
        }
        map.put("MAVEN_OPTS", optsBuilder.toString());
        map.putAll(runnerSettings.getEnvironmentProperties());
        return map;
    }

    /**
     * The home of the bundle maven runs with on the machine of the project.
     */
    private String getJavaHome(Platform platform) throws ExecutionException {
        Sdk sdk = getJdk();
        Path home = sdk == null ? null : sdk.getHomeNioPath();
        if (home == null || !Files.isDirectory(home)) {
            throw new ExecutionException(MavenRunnerLocalize.mavenJavaNotResolved().get());
        }
        return home.toString();
    }

    /**
     * The bundle of the runner when it is of the machine of the project, else a bundle of that machine.
     */
    private @Nullable Sdk getJdk() {
        String targetId = myConfiguration.getProject().getPlatformTargetId();
        String jreName = getMavenRunnerSettings().getJreName();
        Sdk sdk = jreName == null ? null : SdkTable.getInstance().findSdk(jreName);
        if (sdk == null || !Objects.equals(sdk.getPlatformTargetId(), targetId)) {
            String mavenVersion = MavenServerManager.getInstance().getCurrentMavenVersion();
            sdk = MavenJdkUtil.findSdkOfLevel(MavenJdkUtil.getDefaultRunLevel(mavenVersion), null, targetId);
        }
        return sdk;
    }

    /**
     * The JRE of the parameters is the one maven runs with - the debugger compares the JRE it attaches to with it.
     */
    @Override
    protected OwnJavaParameters createJavaParameters() throws ExecutionException {
        OwnJavaParameters parameters = super.createJavaParameters();
        Sdk jdk = getJdk();
        if (jdk != null) {
            parameters.setJdk(jdk);
        }
        return parameters;
    }
}
