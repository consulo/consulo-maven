/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.server.embedder;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadataManager;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionListener;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Activation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.profiles.activation.JdkPrefixProfileActivator;
import org.apache.maven.profiles.activation.OperatingSystemProfileActivator;
import org.apache.maven.profiles.activation.ProfileActivationException;
import org.apache.maven.profiles.activation.ProfileActivator;
import org.apache.maven.profiles.activation.SystemPropertyProfileActivator;
import org.apache.maven.project.DefaultProjectBuilderConfiguration;
import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuilderConfiguration;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectUtils;
import org.apache.maven.project.artifact.ProjectArtifactFactory;
import org.apache.maven.project.inheritance.DefaultModelInheritanceAssembler;
import org.apache.maven.project.injection.DefaultProfileInjector;
import org.apache.maven.project.interpolation.AbstractStringBasedModelInterpolator;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.ModelInterpolator;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeResolutionListener;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.ComponentDependency;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.context.DefaultContext;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.*;
import org.jetbrains.idea.maven.util.MavenFunction;
import org.jetbrains.idea.maven.util.MavenStringUtil;
import org.jetbrains.maven.embedder.MavenEmbedder;
import org.jetbrains.maven.embedder.MavenEmbedderSettings;
import org.jetbrains.maven.embedder.MavenExecutionResult;
import org.jetbrains.maven.embedder.PlexusComponentConfigurator;

public class Maven2ServerEmbedderImpl extends MavenRemoteObject implements MavenServerEmbedder
{
	private final MavenEmbedder myImpl;
	private final Maven2ServerConsoleWrapper myConsoleWrapper;
	private volatile MavenServerProgressIndicator myCurrentIndicator;

	private Maven2ServerEmbedderImpl(MavenEmbedder impl, Maven2ServerConsoleWrapper consoleWrapper)
	{
		myImpl = impl;
		myConsoleWrapper = consoleWrapper;
	}

	public static Maven2ServerEmbedderImpl create(MavenServerSettings facadeSettings) throws RemoteException
	{
		MavenEmbedderSettings settings = new MavenEmbedderSettings();

		List<String> commandLineOptions = new ArrayList<String>();
		String mavenEmbedderCliOptions = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
		if(mavenEmbedderCliOptions != null)
		{
			commandLineOptions.addAll(MavenStringUtil.splitHonorQuotes(mavenEmbedderCliOptions, ' '));
		}

		settings.setConfigurator(new PlexusComponentConfigurator()
		{
			@Override
			public void configureComponents(@Nonnull PlexusContainer c)
			{
				setupContainer(c);
			}
		});
		Maven2ServerConsoleWrapper consoleWrapper = new Maven2ServerConsoleWrapper();
		consoleWrapper.setThreshold(facadeSettings.getLoggingLevel());
		settings.setLogger(consoleWrapper);
		settings.setRecursive(false);

		settings.setWorkOffline(facadeSettings.isOffline());
		settings.setUsePluginRegistry(false);

		settings.setMavenHome(facadeSettings.getMavenHome());
		settings.setUserSettingsFile(facadeSettings.getUserSettingsFile());
		settings.setGlobalSettingsFile(facadeSettings.getGlobalSettingsFile());
		settings.setLocalRepository(facadeSettings.getLocalRepository());

		if(commandLineOptions.contains("-U") || commandLineOptions.contains("--update-snapshots"))
		{
			settings.setSnapshotUpdatePolicy(MavenEmbedderSettings.UpdatePolicy.ALWAYS_UPDATE);
		}
		else
		{
			settings.setSnapshotUpdatePolicy(convertUpdatePolicy(facadeSettings.getSnapshotUpdatePolicy()));
		}
		settings.setPluginUpdatePolicy(convertUpdatePolicy(facadeSettings.getPluginUpdatePolicy()));
		settings.setProperties(MavenServerUtil.collectSystemProperties());

		return new Maven2ServerEmbedderImpl(MavenEmbedder.create(settings), consoleWrapper);
	}

	private static MavenEmbedderSettings.UpdatePolicy convertUpdatePolicy(MavenServerSettings.UpdatePolicy policy) throws RemoteException
	{
		switch(policy)
		{
			case ALWAYS_UPDATE:
				return MavenEmbedderSettings.UpdatePolicy.ALWAYS_UPDATE;
			case DO_NOT_UPDATE:
				return MavenEmbedderSettings.UpdatePolicy.DO_NOT_UPDATE;
			default:
				Maven2ServerGlobals.getLogger().error(new Throwable("unexpected update policy"));
		}
		return MavenEmbedderSettings.UpdatePolicy.DO_NOT_UPDATE;
	}

	private static Collection<String> collectProfilesIds(List<Profile> profiles)
	{
		Collection<String> result = new HashSet<String>();
		for(Profile each : profiles)
		{
			if(each.getId() != null)
			{
				result.add(each.getId());
			}
		}
		return result;
	}

	public static MavenModel interpolateAndAlignModel(MavenModel model, File basedir) throws RemoteException
	{
		Model result = Maven2ModelConverter.toNativeModel(model);
		result = doInterpolate(result, basedir);

		PathTranslator pathTranslator = new DefaultPathTranslator();
		pathTranslator.alignToBaseDirectory(result, basedir);

		return Maven2ModelConverter.convertModel(result, null);
	}

	private static Model doInterpolate(Model result, File basedir) throws RemoteException
	{
		try
		{
			AbstractStringBasedModelInterpolator interpolator = new CustomModelInterpolator(new DefaultPathTranslator());
			interpolator.initialize();

			Properties props = MavenServerUtil.collectSystemProperties();
			ProjectBuilderConfiguration config = new DefaultProjectBuilderConfiguration().setExecutionProperties(props);
			result = interpolator.interpolate(result, basedir, config, false);
		}
		catch(ModelInterpolationException e)
		{
			Maven2ServerGlobals.getLogger().warn(e);
		}
		catch(InitializationException e)
		{
			Maven2ServerGlobals.getLogger().error(e);
		}
		return result;
	}

	public static MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) throws RemoteException
	{
		Model result = Maven2ModelConverter.toNativeModel(model);
		new DefaultModelInheritanceAssembler().assembleModelInheritance(result, Maven2ModelConverter.toNativeModel(parentModel));
		return Maven2ModelConverter.convertModel(result, null);
	}

	public static ProfileApplicationResult applyProfiles(MavenModel model, File basedir, MavenExplicitProfiles explicitProfiles, Collection<String> alwaysOnProfiles) throws RemoteException
	{
		Model nativeModel = Maven2ModelConverter.toNativeModel(model);

		Collection<String> enabledProfiles = explicitProfiles.getEnabledProfiles();
		Collection<String> disabledProfiles = explicitProfiles.getDisabledProfiles();
		List<Profile> activatedPom = new ArrayList<Profile>();
		List<Profile> activatedExternal = new ArrayList<Profile>();
		List<Profile> activeByDefault = new ArrayList<Profile>();

		List<Profile> rawProfiles = nativeModel.getProfiles();
		List<Profile> expandedProfilesCache = null;
		List<Profile> deactivatedProfiles = new ArrayList<Profile>();

		for(int i = 0; i < rawProfiles.size(); i++)
		{
			Profile eachRawProfile = rawProfiles.get(i);

			if(disabledProfiles.contains(eachRawProfile.getId()))
			{
				deactivatedProfiles.add(eachRawProfile);
				continue;
			}

			boolean shouldAdd = enabledProfiles.contains(eachRawProfile.getId()) || alwaysOnProfiles.contains(eachRawProfile.getId());

			Activation activation = eachRawProfile.getActivation();
			if(activation != null)
			{
				if(activation.isActiveByDefault())
				{
					activeByDefault.add(eachRawProfile);
				}

				// expand only if necessary
				if(expandedProfilesCache == null)
				{
					expandedProfilesCache = doInterpolate(nativeModel, basedir).getProfiles();
				}
				Profile eachExpandedProfile = expandedProfilesCache.get(i);

				for(ProfileActivator eachActivator : getProfileActivators(basedir))
				{
					try
					{
						if(eachActivator.canDetermineActivation(eachExpandedProfile) && eachActivator.isActive(eachExpandedProfile))
						{
							shouldAdd = true;
							break;
						}
					}
					catch(ProfileActivationException e)
					{
						Maven2ServerGlobals.getLogger().warn(e);
					}
				}
			}

			if(shouldAdd)
			{
				if(MavenConstants.PROFILE_FROM_POM.equals(eachRawProfile.getSource()))
				{
					activatedPom.add(eachRawProfile);
				}
				else
				{
					activatedExternal.add(eachRawProfile);
				}
			}
		}

		List<Profile> activatedProfiles = new ArrayList<Profile>(activatedPom.isEmpty() ? activeByDefault : activatedPom);
		activatedProfiles.addAll(activatedExternal);

		for(Profile each : activatedProfiles)
		{
			new DefaultProfileInjector().inject(each, nativeModel);
		}

		return new ProfileApplicationResult(Maven2ModelConverter.convertModel(nativeModel, null), new MavenExplicitProfiles(collectProfilesIds(activatedProfiles),
				collectProfilesIds(deactivatedProfiles)));
	}

	private static ProfileActivator[] getProfileActivators(File basedir) throws RemoteException
	{
		SystemPropertyProfileActivator sysPropertyActivator = new SystemPropertyProfileActivator();
		DefaultContext context = new DefaultContext();
		context.put("SystemProperties", MavenServerUtil.collectSystemProperties());
		try
		{
			sysPropertyActivator.contextualize(context);
		}
		catch(ContextException e)
		{
			Maven2ServerGlobals.getLogger().error(e);
			return new ProfileActivator[0];
		}

		return new ProfileActivator[]{
				new MyFileProfileActivator(basedir),
				sysPropertyActivator,
				new JdkPrefixProfileActivator(),
				new OperatingSystemProfileActivator()
		};
	}

	private static void setupContainer(PlexusContainer c)
	{
		MavenEmbedder.setImplementation(c, ArtifactFactory.class, CustomArtifactFactory.class);
		MavenEmbedder.setImplementation(c, ProjectArtifactFactory.class, CustomArtifactFactory.class);
		MavenEmbedder.setImplementation(c, ArtifactResolver.class, CustomArtifactResolver.class);
		MavenEmbedder.setImplementation(c, RepositoryMetadataManager.class, CustomRepositoryMetadataManager.class);
		MavenEmbedder.setImplementation(c, WagonManager.class, CustomWagonManager.class);
		MavenEmbedder.setImplementation(c, ModelInterpolator.class, CustomModelInterpolator.class);
	}

	@Override
	@Nonnull
	public MavenServerExecutionResult resolveProject(@Nonnull final File file,
			@Nonnull final Collection<String> activeProfiles,
			@Nonnull final Collection<String> inactiveProfiles) throws MavenServerProcessCanceledException, RemoteException
	{
		return doExecute(new Executor<MavenServerExecutionResult>()
		{
			@Override
			public MavenServerExecutionResult execute() throws Exception
			{
				DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener(myConsoleWrapper);
				MavenExecutionResult result = myImpl.resolveProject(file, new ArrayList<String>(activeProfiles), new ArrayList<String>(inactiveProfiles), Arrays.<ResolutionListener>asList(listener));
				return createExecutionResult(file, result, listener.getRootNode());
			}
		});
	}

	@Nonnull
	private MavenServerExecutionResult createExecutionResult(File file, MavenExecutionResult result, DependencyNode rootNode) throws RemoteException
	{
		Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();
		Set<MavenId> unresolvedArtifacts = new HashSet<MavenId>();

		validate(file, result.getExceptions(), problems, unresolvedArtifacts);

		MavenProject mavenProject = result.getMavenProject();
		if(mavenProject == null)
		{
			return new MavenServerExecutionResult(null, problems, unresolvedArtifacts);
		}

		MavenModel model = Maven2ModelConverter.convertModel(mavenProject.getModel(), mavenProject.getCompileSourceRoots(), mavenProject.getTestCompileSourceRoots(), mavenProject.getArtifacts(),
				(rootNode == null ? Collections.emptyList() : rootNode.getChildren()), mavenProject.getExtensionArtifacts(), getLocalRepositoryFile());

		RemoteNativeMavenProjectHolder holder = new RemoteNativeMavenProjectHolder(mavenProject);
		try
		{
			UnicastRemoteObject.exportObject(holder, 0);
		}
		catch(RemoteException e)
		{
			throw new RuntimeException(e);
		}

		Collection<String> activatedProfiles = collectActivatedProfiles(mavenProject);

		MavenServerExecutionResult.ProjectData data = new MavenServerExecutionResult.ProjectData(model, Maven2ModelConverter.convertToMap(mavenProject.getModel()), holder, activatedProfiles);
		return new MavenServerExecutionResult(data, problems, unresolvedArtifacts);
	}

	private Collection<String> collectActivatedProfiles(MavenProject mavenProject)
	{
		// for some reason project's active profiles do not contain parent's profiles - only local and settings'.
		// parent's profiles do not contain settings' profiles.

		List<Profile> profiles = new ArrayList<Profile>();
		while(mavenProject != null)
		{
			if(profiles != null)
			{
				profiles.addAll(mavenProject.getActiveProfiles());
			}
			mavenProject = mavenProject.getParent();
		}
		return collectProfilesIds(profiles);
	}

	@Override
	@javax.annotation.Nullable
	public String evaluateEffectivePom(@Nonnull File file, @Nonnull List<String> activeProfiles, @Nonnull List<String> inactiveProfiles)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	@Nonnull
	public MavenArtifact resolve(@Nonnull final MavenArtifactInfo info, @Nonnull final List<MavenRemoteRepository> remoteRepositories) throws MavenServerProcessCanceledException, RemoteException
	{
		return doExecute(new Executor<MavenArtifact>()
		{
			@Override
			public MavenArtifact execute() throws Exception
			{
				return doResolve(info, remoteRepositories);
			}
		});
	}

	@Override
	@Nonnull
	public List<MavenArtifact> resolveTransitively(@Nonnull final List<MavenArtifactInfo> artifacts, @Nonnull final List<MavenRemoteRepository> remoteRepositories) throws RemoteException
	{
		try
		{
			Set<Artifact> toResolve = new LinkedHashSet<Artifact>();
			for(MavenArtifactInfo each : artifacts)
			{
				toResolve.add(createArtifact(each));
			}

			return Maven2ModelConverter.convertArtifacts(myImpl.resolveTransitively(toResolve, convertRepositories(remoteRepositories)), new HashMap<Artifact, MavenArtifact>(),
					getLocalRepositoryFile());
		}
		catch(ArtifactResolutionException e)
		{
			Maven2ServerGlobals.getLogger().info(e);
		}
		catch(ArtifactNotFoundException e)
		{
			Maven2ServerGlobals.getLogger().info(e);
		}
		catch(Exception e)
		{
			throw rethrowException(e);
		}
		return Collections.emptyList();
	}

	private MavenArtifact doResolve(MavenArtifactInfo info, List<MavenRemoteRepository> remoteRepositories) throws RemoteException
	{
		Artifact resolved = doResolve(createArtifact(info), convertRepositories(remoteRepositories));
		return Maven2ModelConverter.convertArtifact(resolved, getLocalRepositoryFile());
	}

	private Artifact createArtifact(MavenArtifactInfo info)
	{
		return getComponent(ArtifactFactory.class).createArtifactWithClassifier(info.getGroupId(), info.getArtifactId(), info.getVersion(), info.getPackaging(), info.getClassifier());
	}

	private Artifact doResolve(Artifact artifact, List<ArtifactRepository> remoteRepositories) throws RemoteException
	{
		try
		{
			myImpl.resolve(artifact, remoteRepositories);
			return artifact;
		}
		catch(Exception e)
		{
			Maven2ServerGlobals.getLogger().info(e);
		}
		return artifact;
	}

	private List<ArtifactRepository> convertRepositories(List<MavenRemoteRepository> repositories) throws RemoteException
	{
		List<ArtifactRepository> result = new ArrayList<ArtifactRepository>();
		for(MavenRemoteRepository each : repositories)
		{
			try
			{
				ArtifactRepositoryFactory factory = getComponent(ArtifactRepositoryFactory.class);
				result.add(ProjectUtils.buildArtifactRepository(Maven2ModelConverter.toNativeRepository(each), factory, getContainer()));
			}
			catch(InvalidRepositoryException e)
			{
				Maven2ServerGlobals.getLogger().warn(e);
			}
		}
		return result;
	}

	@Override
	public Collection<MavenArtifact> resolvePlugin(@Nonnull final MavenPlugin plugin,
												   @Nonnull final List<MavenRemoteRepository> repositories,
												   final int nativeMavenProjectId,
												   final boolean transitive) throws MavenServerProcessCanceledException, RemoteException
	{
		return doExecute(new Executor<Collection<MavenArtifact>>()
		{
			@Override
			public Collection<MavenArtifact> execute() throws Exception
			{
				try
				{
					Plugin mavenPlugin = new Plugin();
					mavenPlugin.setGroupId(plugin.getGroupId());
					mavenPlugin.setArtifactId(plugin.getArtifactId());
					mavenPlugin.setVersion(plugin.getVersion());
					MavenProject project = RemoteNativeMavenProjectHolder.findProjectById(nativeMavenProjectId);
					PluginDescriptor result = getComponent(PluginManager.class).verifyPlugin(mavenPlugin, project, myImpl.getSettings(), myImpl.getLocalRepository());

					Map<MavenArtifactInfo, MavenArtifact> resolvedArtifacts = new HashMap<MavenArtifactInfo, MavenArtifact>();

					Artifact pluginArtifact = result.getPluginArtifact();

					MavenArtifactInfo artifactInfo = new MavenArtifactInfo(pluginArtifact.getGroupId(), pluginArtifact.getArtifactId(), pluginArtifact.getVersion(), pluginArtifact.getType(), null);

					resolveIfNecessary(artifactInfo, repositories, resolvedArtifacts);

					if(transitive)
					{
						// todo try to use parallel downloading
						for(Artifact each : (Iterable<Artifact>) result.getIntroducedDependencyArtifacts())
						{
							resolveIfNecessary(new MavenArtifactInfo(each.getGroupId(), each.getArtifactId(), each.getVersion(), each.getType(), null), repositories, resolvedArtifacts);
						}
						for(ComponentDependency each : (List<ComponentDependency>) result.getDependencies())
						{
							resolveIfNecessary(new MavenArtifactInfo(each.getGroupId(), each.getArtifactId(), each.getVersion(), each.getType(), null), repositories, resolvedArtifacts);
						}
					}

					return new HashSet<MavenArtifact>(resolvedArtifacts.values());
				}
				catch(Exception e)
				{
					Maven2ServerGlobals.getLogger().info(e);
					return Collections.emptyList();
				}
			}
		});
	}

	private void resolveIfNecessary(MavenArtifactInfo info, List<MavenRemoteRepository> repos, Map<MavenArtifactInfo, MavenArtifact> resolvedArtifacts) throws RemoteException
	{
		if(resolvedArtifacts.containsKey(info))
		{
			return;
		}
		resolvedArtifacts.put(info, doResolve(info, repos));
	}

	@Nonnull
	@Override
	public MavenServerExecutionResult execute(@Nonnull final File file,
			@Nonnull final Collection<String> activeProfiles,
			@Nonnull final Collection<String> inactiveProfiles,
			@Nonnull final List<String> goals,
			@Nonnull final List<String> selectedProjects,
			final boolean alsoMake,
			final boolean alsoMakeDependents) throws RemoteException, MavenServerProcessCanceledException
	{
		return doExecute(new Executor<MavenServerExecutionResult>()
		{
			@Override
			public MavenServerExecutionResult execute() throws Exception
			{
				MavenExecutionResult result = myImpl.execute(file, new ArrayList<String>(activeProfiles), new ArrayList<String>(inactiveProfiles), goals, selectedProjects, alsoMake,
						alsoMakeDependents);
				return createExecutionResult(file, result, null);
			}
		});
	}

	private void validate(File file, Collection<Exception> exceptions, Collection<MavenProjectProblem> problems, Collection<MavenId> unresolvedArtifacts) throws RemoteException
	{
		for(Exception each : exceptions)
		{
			Maven2ServerGlobals.getLogger().info(each);

			if(each instanceof InvalidProjectModelException)
			{
				ModelValidationResult modelValidationResult = ((InvalidProjectModelException) each).getValidationResult();
				if(modelValidationResult != null)
				{
					for(Object eachValidationProblem : modelValidationResult.getMessages())
					{
						problems.add(MavenProjectProblem.createStructureProblem(file.getPath(), (String) eachValidationProblem));
					}
				}
				else
				{
					problems.add(MavenProjectProblem.createStructureProblem(file.getPath(), each.getCause().getMessage()));
				}
			}
			else if(each instanceof ProjectBuildingException)
			{
				String causeMessage = each.getCause() != null ? each.getCause().getMessage() : each.getMessage();
				problems.add(MavenProjectProblem.createStructureProblem(file.getPath(), causeMessage));
			}
			else
			{
				problems.add(MavenProjectProblem.createStructureProblem(file.getPath(), each.getMessage()));
			}
		}
		unresolvedArtifacts.addAll(retrieveUnresolvedArtifactIds());
	}

	private Set<MavenId> retrieveUnresolvedArtifactIds()
	{
		Set<MavenId> result = new HashSet<MavenId>();
		((CustomWagonManager) getComponent(WagonManager.class)).getUnresolvedCollector().retrieveUnresolvedIds(result);
		((CustomArtifactResolver) getComponent(ArtifactResolver.class)).getUnresolvedCollector().retrieveUnresolvedIds(result);
		return result;
	}

	@Nonnull
	public File getLocalRepositoryFile()
	{
		return myImpl.getLocalRepositoryFile();
	}

	public <T> T getComponent(Class<T> clazz)
	{
		return myImpl.getComponent(clazz);
	}

	public <T> T getComponent(Class<T> clazz, String roleHint)
	{
		return myImpl.getComponent(clazz, roleHint);
	}

	public PlexusContainer getContainer()
	{
		return myImpl.getContainer();
	}

	@SuppressWarnings("unchecked")
	private <T> T doExecute(final Executor<T> executor) throws MavenServerProcessCanceledException, RemoteException
	{
		final Object[] result = new Object[1];
		final boolean[] cancelled = new boolean[1];
		final Throwable[] exception = new Throwable[1];

		Future<?> future = ExecutorManager.execute(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					result[0] = executor.execute();
				}
				catch(MavenProcessCanceledRuntimeException e)
				{
					cancelled[0] = true;
				}
				catch(Throwable e)
				{
					exception[0] = e;
				}
			}
		});

		MavenServerProgressIndicator indicator = myCurrentIndicator;
		while(true)
		{
			if(indicator.isCanceled())
			{
				throw new MavenServerProcessCanceledException();
			}

			try
			{
				future.get(50, TimeUnit.MILLISECONDS);
			}
			catch(TimeoutException ignore)
			{
			}
			catch(ExecutionException e)
			{
				throw rethrowException(e);
			}
			catch(InterruptedException e)
			{
				throw new MavenServerProcessCanceledException();
			}

			if(future.isDone())
			{
				break;
			}
		}

		if(cancelled[0])
		{
			throw new MavenServerProcessCanceledException();
		}
		if(exception[0] != null)
		{
			if(exception[0] instanceof RuntimeRemoteException)
			{
				throw ((RuntimeRemoteException) exception[0]).getCause();
			}

			throw getRethrowable(exception[0]);
		}

		return (T) result[0];
	}

	private RuntimeException getRethrowable(Throwable throwable)
	{
		if(throwable instanceof InvocationTargetException)
		{
			throwable = throwable.getCause();
		}
		return rethrowException(throwable);
	}

	@Override
	public void customize(@javax.annotation.Nullable MavenWorkspaceMap workspaceMap,
						  boolean failOnUnresolvedDependency,
						  @Nonnull MavenServerConsole console,
						  @Nonnull MavenServerProgressIndicator indicator,
						  boolean alwaysUpdateSnapshots)
	{
		try
		{
			((CustomArtifactFactory) getComponent(ArtifactFactory.class)).customize();
			((CustomArtifactFactory) getComponent(ProjectArtifactFactory.class)).customize();
			((CustomArtifactResolver) getComponent(ArtifactResolver.class)).customize(workspaceMap, failOnUnresolvedDependency);
			((CustomRepositoryMetadataManager) getComponent(RepositoryMetadataManager.class)).customize(workspaceMap);
			((CustomWagonManager) getComponent(WagonManager.class)).customize(failOnUnresolvedDependency);

			setConsoleAndIndicator(console, indicator);
		}
		catch(Exception e)
		{
			throw rethrowException(e);
		}
	}

	@Nonnull
	@Override
	public List<String> retrieveAvailableVersions(@Nonnull String groupId, @Nonnull String artifactId, @Nonnull List<MavenRemoteRepository> remoteRepositories) throws RemoteException
	{
		try
		{
			Artifact artifact = new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(""), Artifact.SCOPE_COMPILE, "pom", null, new DefaultArtifactHandler("pom"));
			ArtifactRepositoryLayout repositoryLayout = getComponent(ArtifactRepositoryLayout.class);
			List versions = getComponent(ArtifactMetadataSource.class).retrieveAvailableVersions(artifact, new DefaultArtifactRepository("local", getLocalRepositoryFile().getPath(),
					repositoryLayout), convertRepositories(remoteRepositories));

			List<String> result = new ArrayList<String>();
			for(Object version : versions)
			{
				result.add(version.toString());
			}
			return result;
		}
		catch(Exception e)
		{
			Maven2ServerGlobals.getLogger().info(e);
		}
		return Collections.emptyList();
	}

	@Override
	public void customizeComponents() throws RemoteException
	{
	}

	private void setConsoleAndIndicator(MavenServerConsole console, MavenServerProgressIndicator indicator)
	{
		myConsoleWrapper.setWrappee(console);
		myCurrentIndicator = indicator;

		WagonManager wagon = getComponent(WagonManager.class);
		wagon.setDownloadMonitor(indicator == null ? null : new TransferListenerAdapter(indicator));
	}

	@Override
	public void reset()
	{
		try
		{
			setConsoleAndIndicator(null, null);

			((CustomArtifactFactory) getComponent(ProjectArtifactFactory.class)).reset();
			((CustomArtifactFactory) getComponent(ArtifactFactory.class)).reset();
			((CustomArtifactResolver) getComponent(ArtifactResolver.class)).reset();
			((CustomRepositoryMetadataManager) getComponent(RepositoryMetadataManager.class)).reset();
			((CustomWagonManager) getComponent(WagonManager.class)).reset();
		}
		catch(Exception e)
		{
			throw rethrowException(e);
		}
	}

	@Override
	public void release()
	{
		try
		{
			myImpl.release();
		}
		catch(Exception e)
		{
			throw rethrowException(e);
		}
	}

	@Override
	public void clearCaches() throws RemoteException
	{
		withProjectCachesDo(new MavenFunction<Map, Object>()
		{
			public Object invoke(Map map)
			{
				map.clear();
				return null;
			}
		});
	}

	@Override
	public void clearCachesFor(final MavenId projectId) throws RemoteException
	{
		withProjectCachesDo(new MavenFunction<Map, Object>()
		{
			public Object invoke(Map map)
			{
				map.remove(projectId.getKey());
				return null;
			}
		});
	}

	private void withProjectCachesDo(MavenFunction<Map, ?> func) throws RemoteException
	{
		MavenProjectBuilder builder = myImpl.getComponent(MavenProjectBuilder.class);
		Field field;
		try
		{
			field = builder.getClass().getDeclaredField("rawProjectCache");
			field.setAccessible(true);
			func.invoke(((Map) field.get(builder)));

			field = builder.getClass().getDeclaredField("processedProjectCache");
			field.setAccessible(true);
			func.invoke(((Map) field.get(builder)));
		}
		catch(NoSuchFieldException e)
		{
			Maven2ServerGlobals.getLogger().info(e);
		}
		catch(IllegalAccessException e)
		{
			Maven2ServerGlobals.getLogger().info(e);
		}
		catch(Exception e)
		{
			throw rethrowException(e);
		}
	}

	private interface Executor<T>
	{
		T execute() throws Exception;
	}
}

