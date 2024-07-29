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
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
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
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.execution.rmi.RemoteProcessSupport;
import consulo.java.execution.OwnSimpleJavaParameters;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.maven.bundle.MavenBundleType;
import consulo.maven.rt.m3.common.MavenServer3CommonMarkerRt;
import consulo.maven.rt.m3.server.MavenServer30MarkerRt;
import consulo.maven.rt.m32.server.MavenServer32MarkerRt;
import consulo.maven.rt.server.common.MavenServerApiMarkerRt;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.maven.rt.server.common.model.MavenModel;
import consulo.maven.rt.server.common.server.*;
import consulo.maven.util.MavenJdkUtil;
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
import consulo.util.lang.Version;
import consulo.util.lang.ref.SimpleReference;
import consulo.util.rmi.RemoteServer;
import consulo.util.xml.serializer.Converter;
import consulo.util.xml.serializer.annotation.Attribute;
import jakarta.inject.Singleton;
import org.apache.lucene.search.Query;
import org.jdom.Document;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

@State(name = "MavenVersion", storages = @Storage("mavenVersion.xml"))
@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class MavenServerManager extends RemoteObjectWrapper<MavenServer> implements PersistentStateComponent<MavenServerManager.State>
{
	private static final String MAIN_CLASS_V3 = "consulo.maven.rt.m3.server.RemoteMavenServer";
	private static final String MAIN_CLASS_V32 = "consulo.maven.rt.m32.server.RemoteMavenServer";

	private static final String DEFAULT_VM_OPTIONS = "-Xmx512m";

	private final RemoteProcessSupport<Object, MavenServer, Object> mySupport;

	private final RemoteMavenServerLogger myLogger = new RemoteMavenServerLogger();
	private final RemoteMavenServerDownloadListener myDownloadListener = new RemoteMavenServerDownloadListener();
	private boolean myLoggerExported;
	private boolean myDownloadListenerExported;

	private State myState = new State();

	static class State
	{
		@Deprecated
		@Attribute(value = "version", converter = UseMavenConverter.class)
		public boolean useMaven2;
		@Attribute
		public String vmOptions = DEFAULT_VM_OPTIONS;
		@Attribute
		public String jdkName;
		@Attribute
		public String mavenBundleName;
		@Attribute
		public MavenExecutionOptions.LoggingLevel loggingLevel = MavenExecutionOptions.LoggingLevel.INFO;
	}

	public static MavenServerManager getInstance()
	{
		return ServiceManager.getService(MavenServerManager.class);
	}

	public MavenServerManager()
	{
		super(null);

		mySupport = new RemoteProcessSupport<Object, MavenServer, Object>(MavenServer.class)
		{
			@Override
			protected void fireModificationCountChanged()
			{
			}

			@Override
			protected String getName(Object file)
			{
				return MavenServerManager.class.getSimpleName();
			}

			@Override
			protected RunProfileState getRunProfileState(Object target, Object configuration, Executor executor) throws ExecutionException
			{
				return createRunProfileState();
			}
		};

		ShutDownTracker.getInstance().registerShutdownTask(() -> shutdown(false));
	}

	@SuppressWarnings("ConstantConditions")
	@Override
	@Nonnull
	protected synchronized MavenServer create() throws RemoteException
	{
		MavenServer result;
		try
		{
			result = mySupport.acquire(this, "");
		}
		catch(Exception e)
		{
			throw new RemoteException("Cannot start maven service", e);
		}

		myLoggerExported = UnicastRemoteObject.exportObject(myLogger, 0) != null;
		if(!myLoggerExported)
		{
			throw new RemoteException("Cannot export logger object");
		}

		myDownloadListenerExported = UnicastRemoteObject.exportObject(myDownloadListener, 0) != null;
		if(!myDownloadListenerExported)
		{
			throw new RemoteException("Cannot export download listener object");
		}

		result.set(myLogger, myDownloadListener);

		return result;
	}

	public synchronized void shutdown(boolean wait)
	{
		mySupport.stopAll(wait);
		cleanup();
	}

	@Override
	protected synchronized void cleanup()
	{
		super.cleanup();

		if(myLoggerExported)
		{
			try
			{
				UnicastRemoteObject.unexportObject(myLogger, true);
			}
			catch(RemoteException e)
			{
				MavenLog.LOG.error(e);
			}
			myLoggerExported = false;
		}
		if(myDownloadListenerExported)
		{
			try
			{
				UnicastRemoteObject.unexportObject(myDownloadListener, true);
			}
			catch(RemoteException e)
			{
				MavenLog.LOG.error(e);
			}
			myDownloadListenerExported = false;
		}
	}

	@Nullable
	private Sdk getSdkForRun(@Nonnull LanguageLevel languageLevel) throws ExecutionException
	{
		String name = myState.jdkName;

		if(name != null)
		{
			return SdkTable.getInstance().findSdk(name);
		}

		return MavenJdkUtil.findSdkOfLevel(languageLevel, null);
	}

	private RunProfileState createRunProfileState() throws ExecutionException
	{
		return new CommandLineState(null)
		{
			@Nonnull
			private OwnSimpleJavaParameters createJavaParameters() throws ExecutionException
			{
				final OwnSimpleJavaParameters params = new OwnSimpleJavaParameters();

				params.setWorkingDirectory(ContainerPathManager.get().getBinPath());

				Map<String, String> defs = new HashMap<>();
				defs.putAll(MavenUtil.getPropertiesFromMavenOpts());

				// pass ssl-related options
				for(Map.Entry<Object, Object> each : System.getProperties().entrySet())
				{
					Object key = each.getKey();
					Object value = each.getValue();
					if(key instanceof String && value instanceof String && ((String) key).startsWith("javax.net.ssl"))
					{
						defs.put((String) key, (String) value);
					}
				}

				defs.put("java.awt.headless", "true");
				for(Map.Entry<String, String> each : defs.entrySet())
				{
					params.getVMParametersList().defineProperty(each.getKey(), each.getValue());
				}

				boolean xmxSet = false;

				if(myState.vmOptions != null)
				{
					ParametersList mavenOptsList = new ParametersList();
					mavenOptsList.addParametersString(myState.vmOptions);

					for(String param : mavenOptsList.getParameters())
					{
						if(param.startsWith("-Xmx"))
						{
							xmxSet = true;
						}

						params.getVMParametersList().add(param);
					}
				}

				final String currentMavenVersion = getCurrentMavenVersion();

				final Sdk jdk = getSdkForRun(MavenJdkUtil.getDefaultRunLevel(currentMavenVersion));
				if(jdk == null)
				{
					throw new IllegalArgumentException("JDK is not found");
				}
				params.setJdk(jdk);

				params.getVMParametersList().addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_VERSION, currentMavenVersion);

				final List<String> classPath = new ArrayList<>();

				classPath.add(ClassPathUtil.getJarPathForClass(RemoteServer.class)); // consulo-util-rmi
				classPath.add(ClassPathUtil.getJarPathForClass(Document.class)); // jdom
				ContainerUtil.addIfNotNull(classPath, ClassPathUtil.getJarPathForClass(Query.class));
				params.getClassPath().addAll(classPath);

				SimpleReference<String> mainClassRef = new SimpleReference<>(MAIN_CLASS_V3);
				params.getClassPath().addAllFiles(collectClassPathAndLibsFolder(mainClassRef));
				params.setMainClass(mainClassRef.get());

				String embedderXmx = System.getProperty("idea.maven.embedder.xmx");
				if(embedderXmx != null)
				{
					params.getVMParametersList().add("-Xmx" + embedderXmx);
				}
				else
				{
					if(!xmxSet)
					{
						params.getVMParametersList().add("-Xmx512m");
					}
				}

				String mavenEmbedderDebugPort = System.getProperty("idea.maven.embedder.debug.port");
				if(mavenEmbedderDebugPort != null)
				{
					params.getVMParametersList().addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=" + mavenEmbedderDebugPort);
				}

				String mavenEmbedderParameters = System.getProperty("idea.maven.embedder.parameters");
				if(mavenEmbedderParameters != null)
				{
					params.getProgramParametersList().addParametersString(mavenEmbedderParameters);
				}

				String mavenEmbedderCliOptions = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
				if(mavenEmbedderCliOptions != null)
				{
					params.getVMParametersList().addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS, mavenEmbedderCliOptions);
				}

				return params;
			}

			@Nonnull
			@Override
			public ExecutionResult execute(@Nonnull Executor executor, @Nonnull ProgramRunner runner) throws ExecutionException
			{
				ProcessHandler processHandler = startProcess();
				return new DefaultExecutionResult(null, processHandler, AnAction.EMPTY_ARRAY);
			}

			@Override
			@Nonnull
			protected ProcessHandler startProcess() throws ExecutionException
			{
				OwnSimpleJavaParameters params = createJavaParameters();
				Sdk sdk = params.getJdk();
				assert sdk != null : "SDK should be defined";

				GeneralCommandLine commandLine = OwnJdkUtil.setupJVMCommandLine(params);

				return ProcessHandlerBuilder.create(commandLine).shouldDestroyProcessRecursively(false).silentReader().build();
			}
		};
	}

	public static File getMavenLibDirectory()
	{
		return new File(getInstance().getCurrentMavenHomeFile(), "lib");
	}

	@Nullable
	public String getMavenVersion(@Nullable String mavenBundleName)
	{
		File file = MavenUtil.resolveMavenHomeDirectory(mavenBundleName);
		if(file != null)
		{
			return MavenUtil.getMavenVersion(file);
		}
		return null;
	}

	@SuppressWarnings("unused")
	@Nullable
	public String getMavenVersion(@Nullable File mavenHome)
	{
		return MavenUtil.getMavenVersion(mavenHome);
	}

	public String getCurrentMavenVersion()
	{
		return getMavenVersion(myState.mavenBundleName);
	}

	@Nullable
	private static Sdk findMaven2Bundle()
	{
		List<Sdk> sdksOfType = SdkTable.getInstance().getSdksOfType(MavenBundleType.getInstance());
		for(Sdk sdk : sdksOfType)
		{
			if(sdk.isPredefined())
			{
				Version version = Version.parseVersion(StringUtil.notNullize(sdk.getVersionString()));
				if(version != null && version.major == 2)
				{
					return sdk;
				}
			}
		}
		return null;
	}


	@Nonnull
	public List<File> collectClassPathAndLibsFolder(SimpleReference<String> mainClassRef)
	{
		final String currentMavenVersion = getCurrentMavenVersion();
		File mavenHome = getCurrentMavenHomeFile();

		File pluginPath = PluginManager.getPluginPath(getClass());
		File libDir = new File(pluginPath, "lib");

		List<File> classpath = new ArrayList<>();

		addJarFromClass(classpath, MavenServerApiMarkerRt.class);

		addJarFromClass(classpath, MavenServer3CommonMarkerRt.class);

		addDir(classpath, new File(libDir, "maven3-server-lib"));

		if (currentMavenVersion == null || StringUtil.compareVersionNumbers(currentMavenVersion, "3.1") < 0) {
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

	private static void addJarFromClass(List<File> files, Class<?> clazz)
	{
		String jarPathForClass = ClassPathUtil.getJarPathForClass(clazz);
		if(jarPathForClass == null)
		{
			throw new RuntimeException("No path for class: " + clazz);
		}
		files.add(new File(jarPathForClass));
	}

	private static void addMavenLibs(List<File> classpath, File mavenHome)
	{
		addDir(classpath, new File(mavenHome, "lib"));
		File bootFolder = new File(mavenHome, "boot");
		File[] classworldsJars = bootFolder.listFiles((dir, name) -> StringUtil.contains(name, "classworlds"));
		if(classworldsJars != null)
		{
			Collections.addAll(classpath, classworldsJars);
		}
	}

	private static void addDir(List<File> classpath, File dir)
	{
		File[] files = dir.listFiles();
		if(files == null)
		{
			return;
		}

		for(File jar : files)
		{
			if(jar.isFile() && jar.getName().endsWith(".jar"))
			{
				classpath.add(jar);
			}
		}
	}

	public MavenEmbedderWrapper createEmbedder(final Project project, final boolean alwaysOnline)
	{
		return new MavenEmbedderWrapper(this)
		{
			@Nonnull
			@Override
			protected MavenServerEmbedder create() throws RemoteException
			{
				MavenServerSettings settings = convertSettings(MavenProjectsManager.getInstance(project).getGeneralSettings());
				if(alwaysOnline && settings.isOffline())
				{
					settings = settings.clone();
					settings.setOffline(false);
				}

				//FIXME [VISTALL] settings.setProjectJdk(MavenUtil.getSdkPath(ProjectRootManager.getInstance(project).getProjectSdk()));

				return MavenServerManager.this.getOrCreateWrappee().createEmbedder(settings);
			}
		};
	}

	public MavenIndexerWrapper createIndexer()
	{
		return new MavenIndexerWrapper(this)
		{
			@Nonnull
			@Override
			protected MavenServerIndexer create() throws RemoteException
			{
				return MavenServerManager.this.getOrCreateWrappee().createIndexer();
			}
		};
	}

	public MavenModel interpolateAndAlignModel(final MavenModel model, final File basedir)
	{
		return perform((Retriable<MavenModel>) () -> getOrCreateWrappee().interpolateAndAlignModel(model, basedir));
	}

	public MavenModel assembleInheritance(final MavenModel model, final MavenModel parentModel)
	{
		return perform((Retriable<MavenModel>) () -> getOrCreateWrappee().assembleInheritance(model, parentModel));
	}

	public ProfileApplicationResult applyProfiles(final MavenModel model, final File basedir, final MavenExplicitProfiles explicitProfiles, final Collection<String> alwaysOnProfiles)
	{
		return perform((Retriable<ProfileApplicationResult>) () -> getOrCreateWrappee().applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles));
	}

	public void addDownloadListener(MavenServerDownloadListener listener)
	{
		myDownloadListener.myListeners.add(listener);
	}

	public void removeDownloadListener(MavenServerDownloadListener listener)
	{
		myDownloadListener.myListeners.remove(listener);
	}

	public static MavenServerSettings convertSettings(MavenGeneralSettings settings)
	{
		MavenServerSettings result = new MavenServerSettings();
		result.setLoggingLevel(settings.getOutputLevel().getLevel());
		result.setOffline(settings.isWorkOffline());
		result.setMavenHome(settings.getEffectiveMavenHome());
		result.setUserSettingsFile(settings.getEffectiveUserSettingsIoFile());
		result.setGlobalSettingsFile(settings.getEffectiveGlobalSettingsIoFile());
		result.setLocalRepository(settings.getEffectiveLocalRepository());
		result.setPluginUpdatePolicy(settings.getPluginUpdatePolicy().getServerPolicy());
		result.setSnapshotUpdatePolicy(settings.isAlwaysUpdateSnapshots() ? MavenServerSettings.UpdatePolicy.ALWAYS_UPDATE : MavenServerSettings.UpdatePolicy.DO_NOT_UPDATE);
		return result;
	}

	public static MavenServerConsole wrapAndExport(final MavenConsole console)
	{
		try
		{
			RemoteMavenServerConsole result = new RemoteMavenServerConsole(console);
			UnicastRemoteObject.exportObject(result, 0);
			return result;
		}
		catch(RemoteException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static MavenServerProgressIndicator wrapAndExport(final MavenProgressIndicator process)
	{
		try
		{
			RemoteMavenServerProgressIndicator result = new RemoteMavenServerProgressIndicator(process);
			UnicastRemoteObject.exportObject(result, 0);
			return result;
		}
		catch(RemoteException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static MavenServerIndicesProcessor wrapAndExport(final MavenIndicesProcessor processor)
	{
		try
		{
			RemoteMavenServerIndicesProcessor result = new RemoteMavenServerIndicesProcessor(processor);
			UnicastRemoteObject.exportObject(result, 0);
			return result;
		}
		catch(RemoteException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static class UseMavenConverter extends Converter<Boolean>
	{
		@Nullable
		@Override
		public Boolean fromString(@Nonnull String value)
		{
			return "2.x".equals(value);
		}

		@Nonnull
		@Override
		public String toString(@Nonnull Boolean value)
		{
			return value ? "2.x" : "3.x";
		}
	}

	public boolean isUsedMaven2ForProjectImport()
	{
		//noinspection deprecation
		return myState.useMaven2;
	}

	public boolean isUseMaven2()
	{
		final String version = getCurrentMavenVersion();
		return StringUtil.compareVersionNumbers(version, "3") < 0 && StringUtil.compareVersionNumbers(version, "2") >= 0;
	}

	@TestOnly
	public void setUseMaven2(boolean useMaven2)
	{
		Sdk maven2Bundle = findMaven2Bundle();
		String newBundleName = useMaven2 ? maven2Bundle == null ? null : maven2Bundle.getName() : null;
		if(!StringUtil.equals(myState.mavenBundleName, newBundleName))
		{
			myState.mavenBundleName = newBundleName;
			shutdown(false);
		}
	}

	@Nullable
	public File getCurrentMavenHomeFile()
	{
		return MavenUtil.resolveMavenHomeDirectory(myState.mavenBundleName);
	}

	public void setMavenBundleName(@Nonnull String mavenHome)
	{
		if(!StringUtil.equals(myState.mavenBundleName, mavenHome))
		{
			myState.mavenBundleName = mavenHome;
			shutdown(false);
		}
	}

	@Nonnull
	public String getMavenEmbedderVMOptions()
	{
		return myState.vmOptions;
	}

	public void setMavenEmbedderVMOptions(@Nonnull String mavenEmbedderVMOptions)
	{
		if(!mavenEmbedderVMOptions.trim().equals(myState.vmOptions.trim()))
		{
			myState.vmOptions = mavenEmbedderVMOptions;
			shutdown(false);
		}
	}

	@Nullable
	public String getJdkName()
	{
		return myState.jdkName;
	}

	public void setJdkName(@Nullable String jdk)
	{
		if(!Comparing.equal(myState.jdkName, jdk))
		{
			myState.jdkName = jdk;
			shutdown(false);
		}
	}

	@Nonnull
	public MavenExecutionOptions.LoggingLevel getLoggingLevel()
	{
		return myState.loggingLevel;
	}

	public void setLoggingLevel(MavenExecutionOptions.LoggingLevel loggingLevel)
	{
		if(myState.loggingLevel != loggingLevel)
		{
			myState.loggingLevel = loggingLevel;
			shutdown(false);
		}
	}

	@Nullable
	@Override
	public State getState()
	{
		return myState;
	}

	@Override
	public void loadState(State state)
	{
		if(state.vmOptions == null)
		{
			state.vmOptions = DEFAULT_VM_OPTIONS;
		}
		myState = state;
	}

	private static class RemoteMavenServerLogger extends MavenRemoteObject implements MavenServerLogger
	{
		@Override
		public void info(Throwable e)
		{
			MavenLog.LOG.info(e);
		}

		@Override
		public void warn(Throwable e)
		{
			MavenLog.LOG.warn(e);
		}

		@Override
		public void error(Throwable e)
		{
			MavenLog.LOG.error(e);
		}

		@Override
		public void print(String s)
		{
			//noinspection UseOfSystemOutOrSystemErr
			System.out.println(s);
		}
	}

	private static class RemoteMavenServerDownloadListener extends MavenRemoteObject implements MavenServerDownloadListener
	{
		private final List<MavenServerDownloadListener> myListeners = Lists.newLockFreeCopyOnWriteList();

		@Override
		public void artifactDownloaded(File file, String relativePath) throws RemoteException
		{
			for(MavenServerDownloadListener each : myListeners)
			{
				each.artifactDownloaded(file, relativePath);
			}
		}
	}

	private static class RemoteMavenServerProgressIndicator extends MavenRemoteObject implements MavenServerProgressIndicator
	{
		private final MavenProgressIndicator myProcess;

		public RemoteMavenServerProgressIndicator(MavenProgressIndicator process)
		{
			myProcess = process;
		}

		@Override
		public void setText(String text)
		{
			myProcess.setText(text);
		}

		@Override
		public void setText2(String text)
		{
			myProcess.setText2(text);
		}

		@Override
		public boolean isCanceled()
		{
			return myProcess.isCanceled();
		}

		@Override
		public void setIndeterminate(boolean value)
		{
			myProcess.getIndicator().setIndeterminate(value);
		}

		@Override
		public void setFraction(double fraction)
		{
			myProcess.setFraction(fraction);
		}
	}

	private static class RemoteMavenServerConsole extends MavenRemoteObject implements MavenServerConsole
	{
		private final MavenConsole myConsole;

		public RemoteMavenServerConsole(MavenConsole console)
		{
			myConsole = console;
		}

		@Override
		public void printMessage(int level, String message, Throwable throwable)
		{
			myConsole.printMessage(level, message, throwable);
		}
	}

	private static class RemoteMavenServerIndicesProcessor extends MavenRemoteObject implements MavenServerIndicesProcessor
	{
		private final MavenIndicesProcessor myProcessor;

		private RemoteMavenServerIndicesProcessor(MavenIndicesProcessor processor)
		{
			myProcessor = processor;
		}

		@Override
		public void processArtifacts(Collection<IndexedMavenId> artifacts)
		{
			myProcessor.processArtifacts(artifacts);
		}
	}
}
