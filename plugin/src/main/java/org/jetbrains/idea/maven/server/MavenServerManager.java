/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.server;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.container.boot.ContainerPathManager;
import consulo.container.plugin.PluginManager;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.execution.DefaultExecutionResult;
import consulo.execution.ExecutionResult;
import consulo.execution.configuration.CommandLineState;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ProgramRunner;
import consulo.java.execution.OwnSimpleJavaParameters;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.maven.rt.m3.common.MavenServer3CommonMarkerRt;
import consulo.maven.rt.m3.server.MavenServer30MarkerRt;
import consulo.maven.rt.m32.server.MavenServer32MarkerRt;
import consulo.maven.rt.m40.server.MavenServer40MarkerRt;
import consulo.maven.rt.server.common.MavenServerApiMarkerRt;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.maven.rt.server.common.model.MavenModel;
import consulo.maven.rt.server.common.server.*;
import consulo.maven.util.MavenJdkUtil;
import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Lists;
import consulo.util.io.ClassPathUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.ShutDownTracker;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.util.rmi.RemoteServer;
import consulo.util.xml.serializer.annotation.Attribute;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import org.apache.lucene.search.Query;
import org.jdom.Document;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@State(name = "MavenVersion", storages = @Storage("mavenVersion.xml"))
@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class MavenServerManager extends RemoteObjectWrapper<MavenServer> implements PersistentStateComponent<MavenServerManager.State> {
    static final String MAIN_CLASS_V3 = "consulo.maven.rt.m3.server.RemoteMavenServer";
    private static final String MAIN_CLASS_V32 = "consulo.maven.rt.m32.server.RemoteMavenServer";
    private static final String MAIN_CLASS_V40 = "consulo.maven.rt.m40.server.RemoteMavenServer";

    private static final String DEFAULT_VM_OPTIONS = "-Xmx512m";

    private @Nullable LocalMavenServerRemoteProcessSupport mySupport;
    // the servers of the machines of projects, by the target id
    private final Map<String, MavenServerConnector> myConnectors = new ConcurrentHashMap<>();

    private final RemoteMavenServerLogger myLogger = new RemoteMavenServerLogger();
    private final RemoteMavenServerDownloadListener myDownloadListener = new RemoteMavenServerDownloadListener();
    private boolean myLoggerExported;
    private boolean myDownloadListenerExported;

    private State myState = new State();

    static class State {
        @Attribute
        public String vmOptions = DEFAULT_VM_OPTIONS;
        @Attribute
        public String jdkName;
        @Attribute
        public String mavenBundleName;
        @Attribute
        public MavenExecutionOptions.LoggingLevel loggingLevel = MavenExecutionOptions.LoggingLevel.INFO;
    }

    public static MavenServerManager getInstance() {
        return Application.get().getInstance(MavenServerManager.class);
    }

    public MavenServerManager() {
        super(null);

        ShutDownTracker.getInstance().registerShutdownTask(() -> shutdown(false));
    }

    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions")
    protected synchronized MavenServer create() throws RemoteException {
        MavenServer result;
        try {
            Sdk jdk = getSdkForRun(MavenJdkUtil.getDefaultRunLevel(getCurrentMavenVersion()), null);
            if (jdk == null) {
                throw new IllegalArgumentException("JDK is not found");
            }
            if (mySupport == null) {
                mySupport = new LocalMavenServerRemoteProcessSupport(jdk, myState.vmOptions, null, null);
            }
            result = mySupport.acquire(this, "");
        }
        catch (Exception e) {
            throw new RemoteException("Cannot start maven service", e);
        }

        attachListeners(result);

        return result;
    }

    /**
     * The logger and the download listener of the IDE - one pair for the servers of all machines.
     */
    synchronized void attachListeners(MavenServer server) throws RemoteException {
        if (!myLoggerExported) {
            myLoggerExported = UnicastRemoteObject.exportObject(myLogger, 0) != null;
            if (!myLoggerExported) {
                throw new RemoteException("Cannot export logger object");
            }
        }

        if (!myDownloadListenerExported) {
            myDownloadListenerExported = UnicastRemoteObject.exportObject(myDownloadListener, 0) != null;
            if (!myDownloadListenerExported) {
                throw new RemoteException("Cannot export download listener object");
            }
        }

        server.set(myLogger, myDownloadListener);
    }

    /**
     * The server of the machine of the project - this manager for a project of the local machine.
     */
    public RemoteObjectWrapper<MavenServer> getConnector(Project project) {
        String targetId = project.getPlatformTargetId();
        if (targetId == null || !MavenRemoteProcessSupportFactory.forProject(project).isApplicable(project)) {
            return this;
        }
        return myConnectors.computeIfAbsent(targetId, id -> {
            Sdk jdk = getSdkForRun(MavenJdkUtil.getDefaultRunLevel(getCurrentMavenVersion()), id);
            if (jdk == null) {
                throw new IllegalArgumentException("JDK of " + project.getPlatform().getName() + " is not found");
            }
            return new MavenServerConnector(project, jdk, myState.vmOptions);
        });
    }

    public synchronized void shutdown(boolean wait) {
        if (mySupport != null) {
            mySupport.stopAll(wait);
            mySupport = null;
        }
        for (MavenServerConnector connector : myConnectors.values()) {
            connector.shutdown(wait);
        }
        myConnectors.clear();
        cleanup();
    }

    @Override
    protected synchronized void cleanup() {
        super.cleanup();

        if (myLoggerExported) {
            try {
                UnicastRemoteObject.unexportObject(myLogger, true);
            }
            catch (RemoteException e) {
                MavenLog.LOG.error(e);
            }
            myLoggerExported = false;
        }
        if (myDownloadListenerExported) {
            try {
                UnicastRemoteObject.unexportObject(myDownloadListener, true);
            }
            catch (RemoteException e) {
                MavenLog.LOG.error(e);
            }
            myDownloadListenerExported = false;
        }
    }

    @Nullable
    private Sdk getSdkForRun(@Nonnull LanguageLevel languageLevel, @Nullable String platformTargetId) {
        String name = myState.jdkName;

        if (name != null) {
            Sdk sdk = SdkTable.getInstance().findSdk(name);
            if (sdk != null && Objects.equals(sdk.getPlatformTargetId(), platformTargetId)) {
                return sdk;
            }
        }

        return MavenJdkUtil.findSdkOfLevel(languageLevel, null, platformTargetId);
    }

    public static File getMavenLibDirectory() {
        return new File(getInstance().getCurrentMavenHomeFile(), "lib");
    }

    @Nullable
    public String getMavenVersion(@Nullable String mavenBundleName) {
        File file = MavenUtil.resolveMavenHomeDirectory(mavenBundleName);
        if (file != null) {
            return MavenUtil.getMavenVersion(file);
        }
        return null;
    }

    @Nullable
    @SuppressWarnings("unused")
    public String getMavenVersion(@Nullable File mavenHome) {
        return MavenUtil.getMavenVersion(mavenHome);
    }

    public String getCurrentMavenVersion() {
        return getMavenVersion(myState.mavenBundleName);
    }

    @Nonnull
    public List<File> collectClassPathAndLibsFolder(SimpleReference<String> mainClassRef) {
        String currentMavenVersion = getCurrentMavenVersion();
        File mavenHome = getCurrentMavenHomeFile();

        File pluginPath = PluginManager.getPluginPath(getClass());
        File libDir = new File(pluginPath, "lib");

        List<File> classpath = new ArrayList<>();

        addJarFromClass(classpath, MavenServerApiMarkerRt.class);

        addJarFromClass(classpath, MavenServer3CommonMarkerRt.class);

        addDir(classpath, new File(libDir, "maven3-server-lib"), f -> true);

        classpath.add(new File(pluginPath, "maven-event-listener.jar"));

        if (currentMavenVersion != null && StringUtil.compareVersionNumbers(currentMavenVersion, "4.0") >= 0) {
            mainClassRef.set(MAIN_CLASS_V40);

            addJarFromClass(classpath, MavenServer40MarkerRt.class);
        }
        else if (currentMavenVersion == null || StringUtil.compareVersionNumbers(currentMavenVersion, "3.1") < 0) {
            mainClassRef.set(MAIN_CLASS_V3);

            addJarFromClass(classpath, MavenServer30MarkerRt.class);
        }
        else {
            mainClassRef.set(MAIN_CLASS_V32);

            addJarFromClass(classpath, MavenServer32MarkerRt.class);
        }

        addMavenLibs(classpath, mavenHome);
        return classpath;
    }

    private static void addJarFromClass(List<File> files, Class<?> clazz) {
        String jarPathForClass = ClassPathUtil.getJarPathForClass(clazz);

        files.add(new File(jarPathForClass));
    }

    private static void addMavenLibs(List<File> classpath, File mavenHome) {
        addDir(classpath, new File(mavenHome, "lib"), f -> !f.getName().contains("maven-slf4j-provider"));
        addDir(classpath, new File(mavenHome, "boot"), file -> true);
    }

    private static void addDir(List<File> classpath, File dir, Predicate<File> filter) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File jar : files) {
            if (jar.isFile() && jar.getName().endsWith(".jar") && filter.test(jar)) {
                classpath.add(jar);
            }
        }
    }

    public MavenEmbedderWrapper createEmbedder(final Project project, final boolean alwaysOnline) {
        RemoteObjectWrapper<MavenServer> connector = getConnector(project);
        return new MavenEmbedderWrapper(connector) {
            @Nonnull
            @Override
            protected MavenServerEmbedder create() throws RemoteException {
                MavenServerSettings settings = convertSettings(MavenProjectsManager.getInstance(project).getGeneralSettings());
                if (alwaysOnline && settings.isOffline()) {
                    settings = settings.clone();
                    settings.setOffline(false);
                }

                //FIXME [VISTALL] settings.setProjectJdk(MavenUtil.getSdkPath(ProjectRootManager.getInstance(project).getProjectSdk()));

                if (connector != MavenServerManager.this) {
                    settings = toServerSettings(settings, project.getPlatform());
                }

                return connector.getOrCreateWrappee().createEmbedder(settings);
            }
        };
    }

    public MavenIndexerWrapper createIndexer() {
        return new MavenIndexerWrapper(this) {
            @Nonnull
            @Override
            protected MavenServerIndexer create() throws RemoteException {
                return MavenServerManager.this.getOrCreateWrappee().createIndexer();
            }
        };
    }

    public MavenModel interpolateAndAlignModel(MavenModel model, File basedir) {
        return perform((Retriable<MavenModel>)() -> getOrCreateWrappee().interpolateAndAlignModel(model, basedir));
    }

    public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
        return perform((Retriable<MavenModel>)() -> getOrCreateWrappee().assembleInheritance(model, parentModel));
    }

    public ProfileApplicationResult applyProfiles(
        MavenModel model,
        File basedir,
        MavenExplicitProfiles explicitProfiles,
        Collection<String> alwaysOnProfiles
    ) {
        return perform((Retriable<ProfileApplicationResult>)() -> getOrCreateWrappee().applyProfiles(
            model,
            basedir,
            explicitProfiles,
            alwaysOnProfiles
        ));
    }

    public void addDownloadListener(MavenServerDownloadListener listener) {
        myDownloadListener.myListeners.add(listener);
    }

    public void removeDownloadListener(MavenServerDownloadListener listener) {
        myDownloadListener.myListeners.remove(listener);
    }

    public static MavenServerSettings convertSettings(MavenGeneralSettings settings) {
        MavenServerSettings result = new MavenServerSettings();
        result.setLoggingLevel(settings.getOutputLevel().getLevel());
        result.setOffline(settings.isWorkOffline());
        result.setMavenHome(settings.getEffectiveMavenHome());
        result.setUserSettingsFile(settings.getEffectiveUserSettingsIoFile());
        result.setGlobalSettingsFile(settings.getEffectiveGlobalSettingsIoFile());
        result.setLocalRepository(settings.getEffectiveLocalRepository());
        result.setPluginUpdatePolicy(settings.getPluginUpdatePolicy().getServerPolicy());
        result.setSnapshotUpdatePolicy(
            settings.isAlwaysUpdateSnapshots()
                ? MavenServerSettings.UpdatePolicy.ALWAYS_UPDATE
                : MavenServerSettings.UpdatePolicy.DO_NOT_UPDATE
        );
        return result;
    }

    /**
     * The settings of a server of another machine: the files of the IDE machine are not there - the server takes the
     * maven home it runs with, the settings and the local repository of the user of that machine.
     */
    private static MavenServerSettings toServerSettings(MavenServerSettings settings, Platform platform) {
        MavenServerSettings result = settings.clone();
        result.setMavenHome(null);
        result.setGlobalSettingsFile(null);
        Path userSettings = platform.user().homePath().resolve(".m2").resolve("settings.xml");
        result.setUserSettingsFile(Files.isRegularFile(userSettings) ? new File(userSettings.toString()) : null);
        result.setLocalRepository(null);
        return result;
    }

    public static MavenServerConsole wrapAndExport(MavenSyncConsole console) {
        try {
            RemoteMavenServerSyncConsole result = new RemoteMavenServerSyncConsole(console);
            UnicastRemoteObject.exportObject(result, 0);
            return result;
        }
        catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public static MavenServerProgressIndicator wrapAndExport(MavenProgressIndicator process) {
        try {
            RemoteMavenServerProgressIndicator result = new RemoteMavenServerProgressIndicator(process);
            UnicastRemoteObject.exportObject(result, 0);
            return result;
        }
        catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public static MavenServerIndicesProcessor wrapAndExport(MavenIndicesProcessor processor) {
        try {
            RemoteMavenServerIndicesProcessor result = new RemoteMavenServerIndicesProcessor(processor);
            UnicastRemoteObject.exportObject(result, 0);
            return result;
        }
        catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public File getCurrentMavenHomeFile() {
        return MavenUtil.resolveMavenHomeDirectory(myState.mavenBundleName);
    }

    public void setMavenBundleName(@Nonnull String mavenHome) {
        if (!StringUtil.equals(myState.mavenBundleName, mavenHome)) {
            myState.mavenBundleName = mavenHome;
            shutdown(false);
        }
    }

    @Nonnull
    public String getMavenEmbedderVMOptions() {
        return myState.vmOptions;
    }

    public void setMavenEmbedderVMOptions(@Nonnull String mavenEmbedderVMOptions) {
        if (!mavenEmbedderVMOptions.trim().equals(myState.vmOptions.trim())) {
            myState.vmOptions = mavenEmbedderVMOptions;
            shutdown(false);
        }
    }

    @Nullable
    public String getJdkName() {
        return myState.jdkName;
    }

    public void setJdkName(@Nullable String jdk) {
        if (!Comparing.equal(myState.jdkName, jdk)) {
            myState.jdkName = jdk;
            shutdown(false);
        }
    }

    @Nonnull
    public MavenExecutionOptions.LoggingLevel getLoggingLevel() {
        return myState.loggingLevel;
    }

    public void setLoggingLevel(MavenExecutionOptions.LoggingLevel loggingLevel) {
        if (myState.loggingLevel != loggingLevel) {
            myState.loggingLevel = loggingLevel;
            shutdown(false);
        }
    }

    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(State state) {
        if (state.vmOptions == null) {
            state.vmOptions = DEFAULT_VM_OPTIONS;
        }
        myState = state;
    }

    private static class RemoteMavenServerLogger extends MavenRemoteObject implements MavenServerLogger {
        @Override
        public void info(Throwable e) {
            MavenLog.LOG.info(e);
        }

        @Override
        public void warn(Throwable e) {
            MavenLog.LOG.warn(e);
        }

        @Override
        public void error(Throwable e) {
            MavenLog.LOG.error(e);
        }

        @Override
        public void print(String s) {
            //noinspection UseOfSystemOutOrSystemErr
            System.out.println(s);
        }
    }

    private static class RemoteMavenServerDownloadListener extends MavenRemoteObject implements MavenServerDownloadListener {
        private final List<MavenServerDownloadListener> myListeners = Lists.newLockFreeCopyOnWriteList();

        @Override
        public void artifactDownloaded(File file, String relativePath) throws RemoteException {
            for (MavenServerDownloadListener each : myListeners) {
                each.artifactDownloaded(file, relativePath);
            }
        }
    }

    private static class RemoteMavenServerProgressIndicator extends MavenRemoteObject implements MavenServerProgressIndicator {
        private final MavenProgressIndicator myProcess;

        public RemoteMavenServerProgressIndicator(MavenProgressIndicator process) {
            myProcess = process;
        }

        @Override
        public void setText(String text) {
            myProcess.setText(text);
        }

        @Override
        public void setText2(String text) {
            myProcess.setText2(text);
        }

        @Override
        public boolean isCanceled() {
            return myProcess.isCanceled();
        }

        @Override
        public void setIndeterminate(boolean value) {
            myProcess.getIndicator().setIndeterminate(value);
        }

        @Override
        public void setFraction(double fraction) {
            myProcess.setFraction(fraction);
        }
    }

    private static class RemoteMavenServerSyncConsole extends MavenRemoteObject implements MavenServerConsole {
        private final MavenSyncConsole myConsole;

        public RemoteMavenServerSyncConsole(MavenSyncConsole console) {
            myConsole = console;
        }

        @Override
        public void printMessage(int level, String message, Throwable throwable) {
            myConsole.printMessage(level, message, throwable);
        }
    }

    private static class RemoteMavenServerIndicesProcessor extends MavenRemoteObject implements MavenServerIndicesProcessor {
        private final MavenIndicesProcessor myProcessor;

        private RemoteMavenServerIndicesProcessor(MavenIndicesProcessor processor) {
            myProcessor = processor;
        }

        @Override
        public void processArtifacts(Collection<IndexedMavenId> artifacts) {
            myProcessor.processArtifacts(artifacts);
        }
    }
}
