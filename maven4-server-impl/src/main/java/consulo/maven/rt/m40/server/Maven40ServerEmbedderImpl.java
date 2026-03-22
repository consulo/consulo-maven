// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.maven.rt.m40.server;

import consulo.maven.rt.m40.server.compat.Maven40InvokerRequestFactory;
import consulo.maven.rt.m40.server.utils.*;
import consulo.maven.rt.server.common.model.*;
import consulo.maven.rt.server.common.server.*;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.api.cli.InvokerException;
import org.apache.maven.api.cli.InvokerRequest;
import org.apache.maven.api.cli.Logger;
import org.apache.maven.api.cli.ParserRequest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.bridge.MavenRepositorySystem;
import org.apache.maven.cling.invoker.ProtoLookup;
import org.apache.maven.cling.invoker.mvn.MavenParser;
import org.apache.maven.execution.*;
import org.apache.maven.internal.impl.DefaultSessionFactory;
import org.apache.maven.internal.impl.InternalMavenSession;
import org.apache.maven.jline.JLineMessageBuilderFactory;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.resolver.MavenChainedWorkspaceReader;
import org.apache.maven.resolver.RepositorySystemSessionFactory;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static consulo.maven.rt.m40.server.utils.Maven40ModelConverter.convertRemoteRepositories;
import static java.util.Objects.requireNonNull;

public class Maven40ServerEmbedderImpl extends MavenRemoteObject implements MavenServerEmbedder {
  private final Maven40Invoker myMavenInvoker;
  private final org.apache.maven.api.services.Lookup myContainer;

  private final Maven40ServerConsoleLogger myConsoleWrapper;

  private final boolean myAlwaysUpdateSnapshots;

  private final MavenRepositorySystem myRepositorySystem;

  private final Maven40ImporterSpy myImporterSpy;

  private final MavenServerSettings mySettings;

  // Set via customize()
  private volatile MavenWorkspaceMap myWorkspaceMap;
  private volatile MavenServerConsole myConsole;
  private volatile MavenServerProgressIndicator myProgressIndicator;
  private volatile MavenServerConsoleIndicator myConsoleIndicator;

  public Maven40ServerEmbedderImpl(MavenServerSettings settings) throws RemoteException {
    mySettings = settings;

    String mavenHome = settings.getMavenHome() != null ? settings.getMavenHome().getPath() : "";

    myConsoleWrapper = new Maven40ServerConsoleLogger();
    myConsoleWrapper.setThreshold(settings.getLoggingLevel());

    ClassWorld classWorld = new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader());
    myMavenInvoker = new Maven40Invoker(
      ProtoLookup.builder().addMapping(ClassWorld.class, classWorld).build(),
      myConsoleWrapper
    );

    String userHomeProperty = System.getProperty("user.home");
    String multiModuleProjectDirectory = "";
    String userHome = userHomeProperty == null ? multiModuleProjectDirectory : userHomeProperty;
    Path mavenHomeDirectory = getCanonicalPath(Paths.get(mavenHome));
    Path userHomeDirectory = getCanonicalPath(Paths.get(userHome));
    Path cwd = getCanonicalPath(Paths.get(multiModuleProjectDirectory.isEmpty() ? "." : multiModuleProjectDirectory));

    List<String> commandLineOptions = new ArrayList<>(settings.getUserProperties().size());
    for (Map.Entry<Object, Object> each : settings.getUserProperties().entrySet()) {
      commandLineOptions.add("-D" + each.getKey() + "=" + each.getValue());
    }

    if (settings.getLocalRepository() != null) {
      commandLineOptions.add("-Dmaven.repo.local=" + settings.getLocalRepository().getPath());
    }
    if (settings.getSnapshotUpdatePolicy() == MavenServerSettings.UpdatePolicy.ALWAYS_UPDATE) {
      commandLineOptions.add("-U");
    }

    if (settings.getLoggingLevel() == MavenServerConsole.LEVEL_DEBUG) {
      commandLineOptions.add("-X");
      commandLineOptions.add("-e");
    }
    else if (settings.getLoggingLevel() == MavenServerConsole.LEVEL_DISABLED) {
      commandLineOptions.add("-q");
    }

    String mavenEmbedderCliOptions = System.getProperty(MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
    if (mavenEmbedderCliOptions != null) {
      commandLineOptions.addAll(Arrays.asList(mavenEmbedderCliOptions.split("\\s+")));
    }

    File globalSettings = settings.getGlobalSettingsFile();
    if (globalSettings != null && globalSettings.isFile()) {
      commandLineOptions.add("-is");
      commandLineOptions.add(globalSettings.getPath());
    }
    File userSettingsFile = settings.getUserSettingsFile();
    if (userSettingsFile != null && userSettingsFile.isFile()) {
      commandLineOptions.add("-s");
      commandLineOptions.add(userSettingsFile.getPath());
    }

    if (settings.isOffline()) {
      commandLineOptions.add("-o");
    }

    // configure our logger to avoid ClassCastException in activateLogging
    System.setProperty("slf4j.provider", Maven40Slf4jServiceProvider.class.getName());
    commandLineOptions.add("-raw-streams");
    commandLineOptions.add("true");
    initLogging(myConsoleWrapper);

    ParserRequest parserRequest = ParserRequest.builder(
        "",
        "",
        commandLineOptions,
        new JLineMessageBuilderFactory()
      )
      .userHome(userHomeDirectory)
      .mavenHome(mavenHomeDirectory)
      .cwd(cwd)
      .build();

    MavenParser mavenParser = new MavenParser() {
      @Override
      public Path getRootDirectory(LocalContext context) {
        Path rootDir = super.getRootDirectory(context);
        if (null == rootDir) {
          return context.topDirectory;
        }
        return rootDir;
      }
    };

    InvokerRequest invokerRequest;
    List<Logger.Entry> entries = new ArrayList<>();
    try {
      invokerRequest = Maven40InvokerRequestFactory.createProxy(mavenParser.parseInvocation(parserRequest));
      entries.addAll(invokerRequest.parserRequest().logger().drain());
      myContainer = myMavenInvoker.invokeAndGetContext(invokerRequest).lookup;
    }
    catch (InvokerException.ExitException e) {
      StringBuilder message = new StringBuilder(e.getMessage());
      for (Logger.Entry entry : entries) {
        if (entry.level() == Logger.Level.ERROR) {
          message.append("\n").append(entry.error().getMessage());
        }
      }
      throw new RuntimeException(message.toString(), e);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    if (myContainer == null) throw new IllegalStateException("Cannot create maven container");

    myAlwaysUpdateSnapshots = commandLineOptions.contains("-U") || commandLineOptions.contains("--update-snapshots");

    Map<String, String> mySystemProperties = invokerRequest.systemProperties();
    if (settings.getProjectJdk() != null) {
      mySystemProperties.put("java.home", settings.getProjectJdk());
    }

    myRepositorySystem = getComponent(MavenRepositorySystem.class);

    Maven40ImporterSpy importerSpy = getComponentIfExists(Maven40ImporterSpy.class);
    if (importerSpy == null) {
      importerSpy = new Maven40ImporterSpy();
    }
    myImporterSpy = importerSpy;
  }

  @Override
  public void customize(MavenWorkspaceMap workspaceMap,
                        boolean failOnUnresolvedDependency,
                        MavenServerConsole console,
                        MavenServerProgressIndicator indicator,
                        boolean alwaysUpdateSnapshots) throws RemoteException {
    myWorkspaceMap = workspaceMap;
    myConsole = console;
    myProgressIndicator = indicator;
    myConsoleWrapper.setWrappee(console);
    if (console != null) {
      myConsoleIndicator = new Maven40LocalConsoleIndicator(console);
      myImporterSpy.setIndicator(myConsoleIndicator);
    }
    // Note: alwaysUpdateSnapshots is handled at construction time via -U flag;
    // if needed, it could also be stored here
  }

  @Override
  public void customizeComponents() throws RemoteException {
    // nothing to do for Maven 4
  }

  @Override
  public List<String> retrieveAvailableVersions(String groupId, String artifactId,
                                                  List<MavenRemoteRepository> remoteRepositories) throws RemoteException {
    // Not implemented
    return Collections.emptyList();
  }

  @Override
  public MavenServerExecutionResult resolveProject(File file,
                                                    Collection<String> activeProfiles,
                                                    Collection<String> inactiveProfiles)
    throws RemoteException, MavenServerProcessCanceledException {
    MavenWorkspaceMap workspaceMap = myWorkspaceMap != null ? myWorkspaceMap : new MavenWorkspaceMap();
    Maven40ProjectResolver projectResolver = new Maven40ProjectResolver(
      this,
      myAlwaysUpdateSnapshots,
      myImporterSpy,
      Collections.singletonList(file),
      new ArrayList<>(activeProfiles),
      new ArrayList<>(inactiveProfiles),
      workspaceMap,
      getLocalRepositoryFile(),
      new Properties()
    );

    try {
      customizeComponents(workspaceMap);
      ArrayList<MavenServerExecutionResult> results = projectResolver.resolveProjects();
      if (results.isEmpty()) {
        return new MavenServerExecutionResult(null, Collections.emptyList(), Collections.emptySet());
      }
      return results.get(0);
    }
    finally {
      resetComponents();
    }
  }

  @Override
  public String evaluateEffectivePom(File file, List<String> activeProfiles, List<String> inactiveProfiles)
    throws RemoteException, MavenServerProcessCanceledException {
    // TODO: implement using Maven40EffectivePomDumper
    throw new UnsupportedOperationException("evaluateEffectivePom not yet implemented for Maven 4");
  }

  @Override
  public MavenArtifact resolve(MavenArtifactInfo info, List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException, MavenServerProcessCanceledException {
    try {
      MavenExecutionRequest request = createRequest(null, null, null);
      final MavenArtifact[] result = {null};

      executeWithMavenSession(request, new MavenWorkspaceMap(), mavenSession -> {
        try {
          List<ArtifactRepository> repos = map2ArtifactRepositories(mavenSession, remoteRepositories, false);
          repos.forEach(request::addRemoteRepository);

          RepositorySystem repositorySystem = getComponent(RepositorySystem.class);
          try {
            ArtifactResult artifactResult = repositorySystem.resolveArtifact(
              mavenSession.getRepositorySession(),
              new ArtifactRequest(RepositoryUtils.toArtifact(createArtifact(info)),
                                  RepositoryUtils.toRepos(repos), null));
            result[0] = Maven40ModelConverter.convertArtifact(
              RepositoryUtils.toArtifact(artifactResult.getArtifact()), getLocalRepositoryFile());
          }
          catch (ArtifactResolutionException e) {
            result[0] = Maven40ModelConverter.convertArtifact(createArtifact(info), getLocalRepositoryFile());
          }
        }
        catch (Exception e) {
          throw wrapToSerializableRuntimeException(e);
        }
      });
      return result[0] != null ? result[0] : Maven40ModelConverter.convertArtifact(createArtifact(info), getLocalRepositoryFile());
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public List<MavenArtifact> resolveTransitively(List<MavenArtifactInfo> artifacts,
                                                   List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException, MavenServerProcessCanceledException {
    // TODO: implement
    return Collections.emptyList();
  }

  @Override
  public Collection<MavenArtifact> resolvePlugin(MavenPlugin plugin,
                                                   List<MavenRemoteRepository> repositories,
                                                   int nativeMavenProjectId,
                                                   boolean transitive)
    throws RemoteException, MavenServerProcessCanceledException {
    try {
      MavenExecutionRequest request = createRequest(null, null, null);
      final List<MavenArtifact> result = new ArrayList<>();

      executeWithMavenSession(request, new MavenWorkspaceMap(), mavenSession -> {
        try {
          List<RemoteRepository> remoteRepos =
            RepositoryUtils.toRepos(map2ArtifactRepositories(mavenSession, repositories, false));

          Plugin mvnPlugin = new Plugin();
          mvnPlugin.setGroupId(plugin.getGroupId());
          mvnPlugin.setArtifactId(plugin.getArtifactId());
          mvnPlugin.setVersion(plugin.getVersion());
          List<Dependency> deps = new ArrayList<>();
          for (MavenId dep : plugin.getDependencies()) {
            Dependency d = new Dependency();
            d.setGroupId(dep.getGroupId());
            d.setArtifactId(dep.getArtifactId());
            d.setVersion(dep.getVersion());
            deps.add(d);
          }
          mvnPlugin.setDependencies(deps);

          PluginDependenciesResolver pluginDependenciesResolver = getComponent(PluginDependenciesResolver.class);

          org.eclipse.aether.artifact.Artifact pluginArtifact =
            pluginDependenciesResolver.resolve(mvnPlugin, remoteRepos, mavenSession.getRepositorySession());

          DependencyFilter dependencyFilter = transitive ? null : (node, parents) -> false;

          DependencyNode node = pluginDependenciesResolver.resolve(
            mvnPlugin, pluginArtifact, dependencyFilter, remoteRepos, mavenSession.getRepositorySession());

          PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
          node.accept(nlg);

          for (org.eclipse.aether.artifact.Artifact artifact : nlg.getArtifacts(true)) {
            result.add(Maven40ModelConverter.convertArtifact(RepositoryUtils.toArtifact(artifact), getLocalRepositoryFile()));
          }
        }
        catch (Exception e) {
          throw wrapToSerializableRuntimeException(e);
        }
      });
      return result;
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenServerExecutionResult execute(File file,
                                             Collection<String> activeProfiles,
                                             Collection<String> inactiveProfiles,
                                             List<String> goals,
                                             List<String> selectedProjects,
                                             boolean alsoMake,
                                             boolean alsoMakeDependents)
    throws RemoteException, MavenServerProcessCanceledException {
    try {
      MavenExecutionRequest request = createRequest(file, new ArrayList<>(activeProfiles), new ArrayList<>(inactiveProfiles));
      request.setGoals(goals);
      if (!selectedProjects.isEmpty()) {
        request.setSelectedProjects(selectedProjects);
      }
      request.setMakeBehavior(alsoMake ? MavenExecutionRequest.REACTOR_MAKE_UPSTREAM
                                       : alsoMakeDependents ? MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM : null);

      Maven maven = getComponent(Maven.class);
      MavenExecutionResult executionResult = maven.execute(request);

      Collection<MavenProjectProblem> problems = collectProblems(file,
                                                                  filterExceptions(executionResult.getExceptions()),
                                                                  Collections.emptyList());
      return new MavenServerExecutionResult(null, problems, Collections.emptySet());
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  private static List<Exception> filterExceptions(List<Throwable> list) {
    for (Throwable throwable : list) {
      if (!(throwable instanceof Exception)) {
        throw new RuntimeException(throwable);
      }
    }
    return (List<Exception>)((List)list);
  }

  @Override
  public void reset() throws RemoteException {
    myWorkspaceMap = null;
    myConsole = null;
    myProgressIndicator = null;
    myConsoleIndicator = null;
    myImporterSpy.setIndicator(null);
    myConsoleWrapper.setWrappee(null);
  }

  @Override
  public void release() throws RemoteException {
    try {
      myMavenInvoker.close();
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public void clearCaches() throws RemoteException {
    // no-op
  }

  @Override
  public void clearCachesFor(MavenId projectId) throws RemoteException {
    // no-op
  }

  public Collection<MavenProjectProblem> collectProblems(File file,
                                                          Collection<? extends Exception> exceptions,
                                                          List<? extends ModelProblem> modelProblems) {
    Collection<MavenProjectProblem> problems = new LinkedHashSet<>();
    for (Throwable each : exceptions) {
      problems.addAll(collectExceptionProblems(file, each));
    }
    for (ModelProblem problem : modelProblems) {
      String source;
      String problemSource = problem.getSource();
      if (problemSource != null && !problemSource.trim().isEmpty()) {
        source = problemSource + ":" + problem.getLineNumber() + ":" + problem.getColumnNumber();
      }
      else {
        source = file == null ? "" : file.getPath();
      }
      String message = "Maven model problem: " +
                       problem.getMessage() +
                       " at " +
                       problem.getSource() +
                       ":" +
                       problem.getLineNumber() +
                       ":" +
                       problem.getColumnNumber();
      if (problem.getSeverity() == ModelProblem.Severity.ERROR) {
        myConsoleWrapper.error(message);
      }
      else {
        myConsoleWrapper.warn(message);
      }
      Exception problemException = problem.getException();
      if (problemException != null) {
        List<MavenProjectProblem> exceptionProblems = collectExceptionProblems(file, problemException);
        if (exceptionProblems.isEmpty()) {
          myConsoleWrapper.error("Maven model problem", problemException);
          problems.add(MavenProjectProblem.createStructureProblem(source, problem.getMessage()));
        }
        else {
          problems.addAll(exceptionProblems);
        }
      }
      else {
        problems.add(MavenProjectProblem.createStructureProblem(source, problem.getMessage(), false));
      }
    }
    return problems;
  }

  private List<MavenProjectProblem> collectExceptionProblems(File file, Throwable ex) {
    List<MavenProjectProblem> result = new ArrayList<>();
    if (ex == null) return result;

    myConsoleWrapper.info("Validation error:", ex);

    Artifact problemTransferArtifact = getProblemTransferArtifact(ex);
    if (ex instanceof IllegalStateException && ex.getCause() != null) {
      ex = ex.getCause();
    }

    String path = file == null ? "" : file.getPath();
    if (path.isEmpty() && ex instanceof ProjectBuildingException) {
      File pomFile = ((ProjectBuildingException)ex).getPomFile();
      path = pomFile == null ? "" : pomFile.getPath();
    }

    if (ex instanceof ProjectBuildingException) {
      String causeMessage = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
      result.add(MavenProjectProblem.createStructureProblem(path, causeMessage));
    }
    else if (ex.getStackTrace().length > 0 && ex.getClass().getPackage().getName().equals("groovy.lang")) {
      myConsoleWrapper.error("Maven server structure problem", ex);
      StackTraceElement traceElement = ex.getStackTrace()[0];
      result.add(MavenProjectProblem.createStructureProblem(
        traceElement.getFileName() + ":" + traceElement.getLineNumber(), ex.getMessage()));
    }
    else if (problemTransferArtifact != null) {
      myConsoleWrapper.error("[server] Maven transfer artifact problem: " + problemTransferArtifact);
      String message = getRootMessage(ex);
      MavenArtifact mavenArtifact = Maven40ModelConverter.convertArtifact(problemTransferArtifact, getLocalRepositoryFile());
      result.add(MavenProjectProblem.createRepositoryProblem(path, message, false, mavenArtifact));
    }
    else {
      myConsoleWrapper.error("Maven server structure problem", ex);
      result.add(MavenProjectProblem.createStructureProblem(path, getRootMessage(ex), false));
    }
    return result;
  }

  public static String getRootMessage(Throwable each) {
    String baseMessage = each.getMessage() != null ? each.getMessage() : "";
    Throwable rootCause = getRootCause(each);
    String rootMessage = rootCause != null ? rootCause.getMessage() : "";
    return rootMessage != null && !rootMessage.isEmpty() ? rootMessage : baseMessage;
  }

  private static Throwable getRootCause(Throwable throwable) {
    Throwable cause = throwable.getCause();
    if (cause == null) return null;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }

  private static Artifact getProblemTransferArtifact(Throwable each) {
    Throwable current = each;
    while (current != null) {
      if (current instanceof ArtifactTransferException) {
        return RepositoryUtils.toArtifact(((ArtifactTransferException)current).getArtifact());
      }
      current = current.getCause();
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public <T> T getComponent(Class<T> clazz) {
    return myContainer.lookup(clazz);
  }

  private <T> T getComponentIfExists(Class<T> clazz) {
    return myContainer.lookupOptional(clazz).orElse(null);
  }

  public MavenExecutionRequest createRequest(File file,
                                              List<String> activeProfiles,
                                              List<String> inactiveProfiles) {
    return createRequest(file, activeProfiles, inactiveProfiles, new Properties());
  }

  public MavenExecutionRequest createRequest(File file,
                                              List<String> activeProfiles,
                                              List<String> inactiveProfiles,
                                              Properties customProperties) {
    try {
      MavenExecutionRequest request = myMavenInvoker.createMavenExecutionRequest();

      activateProfiles(activeProfiles, inactiveProfiles, request);
      Properties userProperties = request.getUserProperties();
      userProperties.putAll(customProperties);
      request.setPom(file);
      return request;
    }
    catch (Exception e) {
      warn(e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }

  private static void activateProfiles(List<String> activeProfiles,
                                        List<String> inactiveProfiles,
                                        MavenExecutionRequest request) {
    ProfileActivation profileActivation = request.getProfileActivation();
    if (null != activeProfiles) {
      for (String profileId : activeProfiles) {
        profileActivation.addProfileActivation(profileId, true, false);
      }
    }
    if (null != inactiveProfiles) {
      for (String profileId : inactiveProfiles) {
        profileActivation.addProfileActivation(profileId, false, false);
      }
    }
  }

  public MavenExecutionResult executeWithMavenSession(MavenExecutionRequest request,
                                                       MavenWorkspaceMap workspaceMap,
                                                       Consumer<MavenSession> runnable) {
    RepositorySystemSessionFactory rsf = getComponent(RepositorySystemSessionFactory.class);
    Maven40RepositorySystemSessionFactory irsf = new Maven40RepositorySystemSessionFactory(
      rsf,
      workspaceMap,
      myProgressIndicator,
      myConsoleIndicator,
      myConsoleWrapper,
      null
    );
    WorkspaceReader workspaceReader = new Maven40WorkspaceMapReader(workspaceMap);
    WorkspaceReader ideWorkspaceReader = getComponentIfExists(WorkspaceReader.class);
    SessionScope sessionScope = getComponent(SessionScope.class);
    DefaultSessionFactory defaultSessionFactory = getComponent(DefaultSessionFactory.class);
    LegacySupport legacySupport = getComponent(LegacySupport.class);

    DefaultMavenExecutionResult result = new DefaultMavenExecutionResult();

    synchronized (this) {
      sessionScope.enter();
      MavenChainedWorkspaceReader chainedWorkspaceReader =
        new MavenChainedWorkspaceReader(workspaceReader, ideWorkspaceReader);
      try (RepositorySystemSession.CloseableSession closeableSession = newCloseableSession(request, chainedWorkspaceReader, irsf)) {
        MavenSession session = new MavenSession(closeableSession, request, result);
        session.setSession(defaultSessionFactory.newSession(session));

        sessionScope.seed(MavenSession.class, session);
        sessionScope.seed(org.apache.maven.api.Session.class, session.getSession());
        sessionScope.seed(InternalMavenSession.class, InternalMavenSession.from(session.getSession()));

        legacySupport.setSession(session);

        afterSessionStart(session);

        runnable.accept(session);
      }
      finally {
        legacySupport.setSession(null);
        sessionScope.exit();
      }
    }

    return result;
  }

  private static RepositorySystemSession.CloseableSession newCloseableSession(MavenExecutionRequest request,
                                                                               WorkspaceReader workspaceReader,
                                                                               RepositorySystemSessionFactory factory) {
    return factory
      .newRepositorySessionBuilder(request)
      .setWorkspaceReader(workspaceReader)
      .build();
  }

  private void afterSessionStart(MavenSession mavenSession) {
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    Collection<AbstractMavenLifecycleParticipant> lifecycleParticipants =
      getExtensionComponents(Collections.emptyList(), AbstractMavenLifecycleParticipant.class);
    for (AbstractMavenLifecycleParticipant listener : lifecycleParticipants) {
      Thread.currentThread().setContextClassLoader(listener.getClass().getClassLoader());
      try {
        listener.afterSessionStart(mavenSession);
      }
      catch (MavenExecutionException e) {
        throw new RuntimeException(e);
      }
      finally {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
      }
    }
  }

  /**
   * adapted from {@link DefaultMaven#getExtensionComponents(Collection, Class)}
   */
  public <T> Collection<T> getExtensionComponents(Collection<MavenProject> projects, Class<T> role) {
    Collection<T> foundComponents = new LinkedHashSet<>(getContainer().lookupList(role));
    foundComponents.addAll(getProjectScopedExtensionComponents(projects, role));
    return foundComponents;
  }

  protected <T> Collection<T> getProjectScopedExtensionComponents(Collection<MavenProject> projects, Class<T> role) {
    if (projects == null) {
      return Collections.emptyList();
    }

    Collection<T> foundComponents = new LinkedHashSet<>();
    Collection<ClassLoader> scannedRealms = new HashSet<>();

    Thread currentThread = Thread.currentThread();
    ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    try {
      for (MavenProject project : projects) {
        ClassLoader projectRealm = project.getClassRealm();
        if (projectRealm != null && scannedRealms.add(projectRealm)) {
          currentThread.setContextClassLoader(projectRealm);
          foundComponents.addAll(getContainer().lookupList(role));
        }
      }
      return foundComponents;
    }
    finally {
      currentThread.setContextClassLoader(originalContextClassLoader);
    }
  }

  public void warn(String message, Throwable e) {
    myConsoleWrapper.warn(message, e);
  }

  public RuntimeException wrapToSerializableRuntimeException(Exception e) {
    Throwable wrap = wrapException(e);
    return wrap instanceof RuntimeException ? (RuntimeException)wrap : new RuntimeException(wrap);
  }

  private org.apache.maven.api.services.Lookup getContainer() {
    return myContainer;
  }

  private File getLocalRepositoryFile() {
    return mySettings.getLocalRepository() != null ? mySettings.getLocalRepository() : new File(System.getProperty("user.home"), ".m2/repository");
  }

  private void customizeComponents(MavenWorkspaceMap workspaceMap) {
    // TODO: implement
  }

  private void resetComponents() {
    // TODO: implement
  }

  private static void initLogging(Maven40ServerConsoleLogger consoleWrapper) {
    Maven40Sl4jLoggerWrapper.setCurrentWrapper(consoleWrapper);
  }

  private List<ArtifactRepository> map2ArtifactRepositories(MavenSession session,
                                                              List<MavenRemoteRepository> repositories,
                                                              boolean forceResolveSnapshots) {
    List<ArtifactRepository> result = new ArrayList<>();
    for (MavenRemoteRepository each : repositories) {
      try {
        result.add(buildArtifactRepository(session, Maven40ModelConverter.toNativeRepository(each, forceResolveSnapshots)));
      }
      catch (InvalidRepositoryException e) {
        myConsoleWrapper.warn("Invalid repository: " + e.getMessage(), e);
      }
    }
    return result;
  }

  private ArtifactRepository buildArtifactRepository(MavenSession session, Repository repo) throws InvalidRepositoryException {
    MavenRepositorySystem repositorySystem = myRepositorySystem;
    ArtifactRepository repository = MavenRepositorySystem.buildArtifactRepository(repo);

    RepositorySystemSession repositorySession = session == null ? null : session.getRepositorySession();
    if (repositorySession != null) {
      repositorySystem.injectMirror(repositorySession, Collections.singletonList(repository));
      repositorySystem.injectProxy(repositorySession, Collections.singletonList(repository));
      repositorySystem.injectAuthentication(repositorySession, Collections.singletonList(repository));
    }
    return repository;
  }

  private Artifact createArtifact(MavenArtifactInfo info) {
    return getComponent(ArtifactFactory.class)
      .createArtifactWithClassifier(info.getGroupId(), info.getArtifactId(), info.getVersion(), info.getPackaging(), info.getClassifier());
  }

  public static Path getCanonicalPath(Path path) {
    requireNonNull(path, "path");
    try {
      return path.toRealPath();
    }
    catch (IOException e) {
      if (path.getParent() != null) {
        return getCanonicalPath(path.getParent()).resolve(path.getFileName());
      }
      return path.toAbsolutePath();
    }
  }
}
