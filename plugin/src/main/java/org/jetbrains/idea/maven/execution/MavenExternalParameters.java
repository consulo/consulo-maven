/* ==========================================================================
 * Copyright 2006 Mevenide Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */


package org.jetbrains.idea.maven.execution;

import com.intellij.java.language.LanguageLevel;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.container.boot.ContainerPathManager;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.execution.RunConfigurationEditor;
import consulo.execution.RunManager;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.maven.rt.server.common.server.MavenServerUtil;
import consulo.maven.util.MavenJdkUtil;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.process.ExecutionException;
import consulo.process.cmd.ParametersList;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.event.NotificationListener;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.io.ClassPathUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.encoding.EncodingManager;
import consulo.virtualFileSystem.encoding.EncodingProjectManager;
import org.jetbrains.idea.maven.artifactResolver.common.MavenModuleMap;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.rt.m3.MavenArtifactResolvedM3RtMarker;
import org.jetbrains.idea.maven.rt.m31.MavenArtifactResolvedM31RtMarker;
import org.jetbrains.idea.maven.utils.MavenSettings;
import org.jetbrains.idea.maven.utils.MavenUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.io.*;
import java.util.*;

/**
 * @author Ralf Quebbemann
 */
public class MavenExternalParameters {
    private static final Logger LOG = Logger.getInstance(MavenExternalParameters.class);

    public static final String MAVEN_LAUNCHER_CLASS = "org.codehaus.classworlds.Launcher";

    private static final String MAVEN_OPTS = "MAVEN_OPTS";

    @Deprecated // Use createJavaParameters(Project,MavenRunnerParameters, MavenGeneralSettings,MavenRunnerSettings,MavenRunConfiguration)
    @RequiredReadAction
    public static OwnJavaParameters createJavaParameters(
        @Nullable final Project project,
        @Nonnull final MavenRunnerParameters parameters,
        @Nullable MavenGeneralSettings coreSettings,
        @Nullable MavenRunnerSettings runnerSettings
    ) throws ExecutionException {
        return createJavaParameters(project, parameters, coreSettings, runnerSettings, null);
    }

    @RequiredReadAction
    public static OwnJavaParameters createJavaParameters(
        @Nullable final Project project,
        @Nonnull final MavenRunnerParameters parameters
    ) throws ExecutionException {
        return createJavaParameters(project, parameters, null, null, null);
    }

    /**
     * @param project
     * @param parameters
     * @param coreSettings
     * @param runnerSettings
     * @param runConfiguration used to creation fix if maven home not found
     * @return
     * @throws ExecutionException
     */
    @RequiredReadAction
    public static OwnJavaParameters createJavaParameters(
        @Nullable final Project project,
        @Nonnull final MavenRunnerParameters parameters,
        @Nullable MavenGeneralSettings coreSettings,
        @Nullable MavenRunnerSettings runnerSettings,
        @Nullable MavenRunConfiguration runConfiguration
    ) throws ExecutionException {
        final OwnJavaParameters params = new OwnJavaParameters();

        Application.get().assertReadAccessAllowed();

        if (coreSettings == null) {
            coreSettings = project == null ? new MavenGeneralSettings() : MavenProjectsManager.getInstance(project).getGeneralSettings();
        }
        if (runnerSettings == null) {
            runnerSettings = project == null ? new MavenRunnerSettings() : MavenRunner.getInstance(project).getState();
        }

        params.setWorkingDirectory(parameters.getWorkingDirFile());

        final String mavenHome = resolveMavenHome(coreSettings, project, runConfiguration);
        final String mavenVersion = MavenUtil.getMavenVersion(mavenHome);

        LanguageLevel defaultRunLevel = MavenJdkUtil.getDefaultRunLevel(mavenVersion);

        Sdk jdk = getJdk(
            runnerSettings,
            defaultRunLevel,
            project != null && MavenRunner.getInstance(project).getState() == runnerSettings
        );

        params.setJdk(jdk);

        if (StringUtil.compareVersionNumbers(mavenVersion, "3.3") >= 0) {
            params.getVMParametersList().addProperty(
                "maven.multiModuleProjectDirectory",
                MavenServerUtil.findMavenBasedir(parameters.getWorkingDirFile()).getPath()
            );
        }

        addVMParameters(params.getVMParametersList(), mavenHome, runnerSettings);

        File confFile = MavenUtil.getMavenConfFile(new File(mavenHome));
        if (!confFile.isFile()) {
            throw new ExecutionException("Configuration file is not exists in maven home: " + confFile.getAbsolutePath());
        }

        if (project != null && parameters.isResolveToWorkspace()) {
            try {
                confFile = patchConfFile(confFile, getArtifactResolverJars(mavenVersion));

                File modulesPathsFile = dumpModulesPaths(project);
                params.getVMParametersList().addProperty(MavenModuleMap.PATHS_FILE_PROPERTY, modulesPathsFile.getAbsolutePath());
            }
            catch (IOException e) {
                LOG.error(e);
                throw new ExecutionException("Failed to run maven configuration", e);
            }
        }

        params.getVMParametersList().addProperty("classworlds.conf", confFile.getPath());

        for (String path : getMavenClasspathEntries(mavenHome)) {
            params.getClassPath().add(path);
        }

        params.setEnv(new HashMap<>(runnerSettings.getEnvironmentProperties()));
        params.setPassParentEnvs(runnerSettings.isPassParentEnv());

        params.setMainClass(MAVEN_LAUNCHER_CLASS);
        EncodingManager encodingManager = project == null ? EncodingManager.getInstance() : EncodingProjectManager.getInstance(project);
        params.setCharset(encodingManager.getDefaultCharset());

        addMavenParameters(params.getProgramParametersList(), mavenHome, coreSettings, runnerSettings, parameters);

        return params;
    }

    private static File patchConfFile(File conf, List<String> libraries) throws IOException {
        File tmpConf = FileUtil.createTempFile("idea-", "-mvn.conf");
        tmpConf.deleteOnExit();
        patchConfFile(conf, tmpConf, libraries);

        return tmpConf;
    }

    private static void patchConfFile(File originalConf, File dest, List<String> libraries) throws IOException {
        try (Scanner sc = new Scanner(originalConf)) {
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dest)))) {
                boolean patched = false;

                while (sc.hasNextLine()) {
                    String line = sc.nextLine();

                    out.append(line);
                    out.newLine();

                    if (!patched && "[plexus.core]".equals(line)) {
                        for (String library : libraries) {
                            out.append("load ").append(library);
                            out.newLine();
                        }

                        patched = true;
                    }
                }
            }
        }
    }

    private static List<String> getArtifactResolverJars(@Nullable String mavenVersion) throws IOException {
        Class marker = MavenArtifactResolvedM3RtMarker.class;

        if (mavenVersion != null) {
            if (StringUtil.compareVersionNumbers(mavenVersion, "3.1") >= 0) {
                marker = MavenArtifactResolvedM31RtMarker.class;
            }
        }

        List<String> classpath = new ArrayList<>(2);
        classpath.add(ClassPathUtil.getJarPathForClass(MavenModuleMap.class));
        classpath.add(ClassPathUtil.getJarPathForClass(marker));
        return classpath;
    }

    @RequiredReadAction
    private static File dumpModulesPaths(@Nonnull Project project) throws IOException {
        project.getApplication().assertReadAccessAllowed();

        Properties res = new Properties();

        MavenProjectsManager manager = MavenProjectsManager.getInstance(project);

        for (Module module : ModuleManager.getInstance(project).getModules()) {
            if (manager.isMavenizedModule(module)) {
                MavenProject mavenProject = manager.findProject(module);
                if (mavenProject != null && !manager.isIgnored(mavenProject)) {
                    res.setProperty(
                        mavenProject.getMavenId().getGroupId() + ':' +
                            mavenProject.getMavenId().getArtifactId() + ":pom:" +
                            mavenProject.getMavenId().getVersion(),
                        mavenProject.getFile().getPath()
                    );

                    res.setProperty(
                        mavenProject.getMavenId().getGroupId() + ':' +
                            mavenProject.getMavenId().getArtifactId() + ":test-jar:" +
                            mavenProject.getMavenId().getVersion(),
                        mavenProject.getTestOutputDirectory()
                    );

                    res.setProperty(
                        mavenProject.getMavenId().getGroupId() + ':' +
                            mavenProject.getMavenId().getArtifactId() + ':' +
                            mavenProject.getPackaging() + ':' +
                            mavenProject.getMavenId().getVersion(),
                        mavenProject.getOutputDirectory()
                    );

                }
            }
        }

        File file = new File(
            ContainerPathManager.get().getSystemPath(),
            "Maven/idea-projects-state-" + project.getLocationHash() + ".properties"
        );
        FileUtil.ensureExists(file.getParentFile());

        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            res.store(out, null);
        }

        return file;
    }

    @Nullable
    private static Sdk getJdk(MavenRunnerSettings runnerSettings, LanguageLevel languageLevel, boolean isGlobalRunnerSettings)
        throws ExecutionException {
        String name = runnerSettings.getJreName();

        if (name != null) {
            return SdkTable.getInstance().findSdk(name);
        }

        Sdk sdk = MavenJdkUtil.findSdkOfLevel(languageLevel, null);
        if (sdk != null) {
            return sdk;
        }

        throw new ExecutionException(MavenRunnerLocalize.mavenJavaNotResolved().get());
    }

    public static void addVMParameters(ParametersList parametersList, String mavenHome, MavenRunnerSettings runnerSettings) {
        parametersList.addParametersString(System.getenv(MAVEN_OPTS));

        parametersList.addParametersString(runnerSettings.getVmOptions());

        parametersList.addProperty("maven.home", mavenHome);
    }

    private static void addMavenParameters(
        ParametersList parametersList,
        String mavenHome,
        MavenGeneralSettings coreSettings,
        MavenRunnerSettings runnerSettings,
        MavenRunnerParameters parameters
    ) {
        encodeCoreAndRunnerSettings(coreSettings, mavenHome, parametersList);

        if (runnerSettings.isSkipTests()) {
            parametersList.addProperty("skipTests", "true");
        }

        for (Map.Entry<String, String> entry : runnerSettings.getMavenProperties().entrySet()) {
            if (entry.getKey().length() > 0) {
                parametersList.addProperty(entry.getKey(), entry.getValue());
            }
        }

        for (String goal : parameters.getGoals()) {
            parametersList.add(goal);
        }

        addOption(parametersList, "P", encodeProfiles(parameters.getProfilesMap()));
    }

    private static void addOption(ParametersList cmdList, String key, String value) {
        if (!StringUtil.isEmptyOrSpaces(value)) {
            cmdList.add("-" + key);
            cmdList.add(value);
        }
    }

    @Nonnull
    public static String resolveMavenHome(@Nonnull MavenGeneralSettings coreSettings) throws ExecutionException {
        return resolveMavenHome(coreSettings, null, null);
    }

    /**
     * @param coreSettings
     * @param project          used to creation fix if maven home not found
     * @param runConfiguration used to creation fix if maven home not found
     * @return
     * @throws ExecutionException
     */
    @Nonnull
    public static String resolveMavenHome(
        @Nonnull MavenGeneralSettings coreSettings,
        @Nullable Project project,
        @Nullable MavenRunConfiguration runConfiguration
    ) throws ExecutionException {
        final File file = MavenUtil.resolveMavenHomeDirectory(coreSettings.getMavenBundleName());

        if (file == null) {
            throw createExecutionException(
                MavenRunnerLocalize.externalMavenHomeNoDefault(),
                MavenRunnerLocalize.externalMavenHomeNoDefaultWithFix(),
                coreSettings,
                project,
                runConfiguration
            );
        }

        if (!file.exists()) {
            throw createExecutionException(
                MavenRunnerLocalize.externalMavenHomeDoesNotExist(file.getPath()),
                MavenRunnerLocalize.externalMavenHomeDoesNotExistWithFix(file.getPath()),
                coreSettings,
                project,
                runConfiguration
            );
        }

        if (!MavenUtil.isValidMavenHome(file)) {
            throw createExecutionException(
                MavenRunnerLocalize.externalMavenHomeInvalid(file.getPath()),
                MavenRunnerLocalize.externalMavenHomeInvalidWithFix(file.getPath()),
                coreSettings,
                project,
                runConfiguration
            );
        }

        try {
            return file.getCanonicalPath();
        }
        catch (IOException e) {
            throw new ExecutionException(e.getMessage(), e);
        }
    }

    private static ExecutionException createExecutionException(
        LocalizeValue text,
        LocalizeValue textWithFix,
        @Nonnull MavenGeneralSettings coreSettings,
        @Nullable Project project,
        @Nullable MavenRunConfiguration runConfiguration
    ) {
        Project notNullProject = project;
        if (notNullProject == null) {
            if (runConfiguration == null) {
                return new ExecutionException(text.get());
            }
            notNullProject = runConfiguration.getProject();
            if (notNullProject == null) {
                return new ExecutionException(text.get());
            }
        }

        if (coreSettings == MavenProjectsManager.getInstance(notNullProject).getGeneralSettings()) {
            return new ProjectSettingsOpenerExecutionException(textWithFix.get(), notNullProject);
        }

        if (runConfiguration != null) {
            Project runCfgProject = runConfiguration.getProject();
            if (runCfgProject != null) {
                if (RunManager.getInstance(runCfgProject).findSettings(runConfiguration) != null) {
                    return new RunConfigurationOpenerExecutionException(textWithFix.get(), runConfiguration);
                }
            }
        }

        return new ExecutionException(text.get());
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private static List<String> getMavenClasspathEntries(final String mavenHome) {
        File mavenHomeBootAsFile = new File(new File(mavenHome, "core"), "boot");
        // if the dir "core/boot" does not exist we are using a Maven version > 2.0.5
        // in this case the classpath must be constructed from the dir "boot"
        if (!mavenHomeBootAsFile.exists()) {
            mavenHomeBootAsFile = new File(mavenHome, "boot");
        }

        List<String> classpathEntries = new ArrayList<>();

        File[] files = mavenHomeBootAsFile.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().contains("classworlds")) {
                    classpathEntries.add(file.getAbsolutePath());
                }
            }
        }

        return classpathEntries;
    }

    private static void encodeCoreAndRunnerSettings(MavenGeneralSettings coreSettings, String mavenHome, ParametersList cmdList) {
        if (coreSettings.isWorkOffline()) {
            cmdList.add("--offline");
        }

        boolean atLeastMaven3 = MavenUtil.isMaven3(mavenHome);

        if (!atLeastMaven3) {
            addIfNotEmpty(cmdList, coreSettings.getPluginUpdatePolicy().getCommandLineOption());

            if (!coreSettings.isUsePluginRegistry()) {
                cmdList.add("--no-plugin-registry");
            }
        }

        if (coreSettings.getOutputLevel() == MavenExecutionOptions.LoggingLevel.DEBUG) {
            cmdList.add("--debug");
        }
        if (coreSettings.isNonRecursive()) {
            cmdList.add("--non-recursive");
        }
        if (coreSettings.isPrintErrorStackTraces()) {
            cmdList.add("--errors");
        }

        if (coreSettings.isAlwaysUpdateSnapshots()) {
            cmdList.add("--update-snapshots");
        }

        if (StringUtil.isNotEmpty(coreSettings.getThreads())) {
            cmdList.add("-T", coreSettings.getThreads());
        }

        addIfNotEmpty(cmdList, coreSettings.getFailureBehavior().getCommandLineOption());
        addIfNotEmpty(cmdList, coreSettings.getChecksumPolicy().getCommandLineOption());

        addOption(cmdList, "s", coreSettings.getUserSettingsFile());
        if (!StringUtil.isEmptyOrSpaces(coreSettings.getLocalRepository())) {
            cmdList.addProperty("maven.repo.local", coreSettings.getLocalRepository());
        }
    }

    private static void addIfNotEmpty(ParametersList parametersList, @Nullable String value) {
        if (!StringUtil.isEmptyOrSpaces(value)) {
            parametersList.add(value);
        }
    }

    private static String encodeProfiles(Map<String, Boolean> profiles) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, Boolean> entry : profiles.entrySet()) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append(",");
            }
            if (!entry.getValue()) {
                stringBuilder.append("!");
            }
            stringBuilder.append(entry.getKey());
        }
        return stringBuilder.toString();
    }

    private static class ProjectSettingsOpenerExecutionException extends WithHyperlinkExecutionException {
        private final Project myProject;

        public ProjectSettingsOpenerExecutionException(final String s, Project project) {
            super(s);
            myProject = project;
        }

        @Override
        @RequiredUIAccess
        protected void hyperlinkClicked() {
            ShowSettingsUtil.getInstance().showSettingsDialog(myProject, MavenSettings.DISPLAY_NAME);
        }
    }

    private static class ProjectJdkSettingsOpenerExecutionException extends WithHyperlinkExecutionException {
        private final Project myProject;

        public ProjectJdkSettingsOpenerExecutionException(final String s, Project project) {
            super(s);
            myProject = project;
        }

        @Override
        @RequiredUIAccess
        protected void hyperlinkClicked() {
            ShowSettingsUtil.getInstance().showProjectStructureDialog(myProject);
        }
    }

    private static class RunConfigurationOpenerExecutionException extends WithHyperlinkExecutionException {

        private final MavenRunConfiguration myRunConfiguration;

        public RunConfigurationOpenerExecutionException(final String s, MavenRunConfiguration runConfiguration) {
            super(s);
            myRunConfiguration = runConfiguration;
        }

        @Override
        @RequiredUIAccess
        protected void hyperlinkClicked() {
            Project project = myRunConfiguration.getProject();

            RunConfigurationEditor editor = RunConfigurationEditor.getInstance(project);

            editor.editAll();
        }
    }

    private static abstract class WithHyperlinkExecutionException extends ExecutionException implements HyperlinkListener, NotificationListener {
        public WithHyperlinkExecutionException(String s) {
            super(s);
        }

        protected abstract void hyperlinkClicked();

        @Override
        public final void hyperlinkUpdate(HyperlinkEvent e) {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                hyperlinkClicked();
            }
        }

        @Override
        @RequiredUIAccess
        public final void hyperlinkUpdate(@Nonnull Notification notification, @Nonnull HyperlinkEvent event) {
            hyperlinkUpdate(event);
        }
    }
}
