// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.maven.rt.m40.server.utils;

import consulo.maven.rt.m40.server.Maven40ServerEmbedderImpl;
import consulo.maven.rt.server.common.model.*;
import consulo.maven.rt.server.common.server.MavenServerExecutionResult;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.DefaultMaven;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.project.*;
import org.apache.maven.resolver.MavenChainedWorkspaceReader;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Maven40ProjectResolver {
  private final Maven40ServerEmbedderImpl myEmbedder;
  private final boolean myUpdateSnapshots;
  private final Maven40ImporterSpy myImporterSpy;
  private final List<File> myFilesToResolve;
  private final List<String> myActiveProfiles;
  private final List<String> myInactiveProfiles;
  private final MavenWorkspaceMap myWorkspaceMap;
  private final File myLocalRepositoryFile;
  private final Properties myUserProperties;

  public Maven40ProjectResolver(Maven40ServerEmbedderImpl embedder,
                                 boolean updateSnapshots,
                                 Maven40ImporterSpy importerSpy,
                                 List<File> filesToResolve,
                                 List<String> activeProfiles,
                                 List<String> inactiveProfiles,
                                 MavenWorkspaceMap workspaceMap,
                                 File localRepositoryFile,
                                 Properties userProperties) {
    myEmbedder = embedder;
    myUpdateSnapshots = updateSnapshots;
    myImporterSpy = importerSpy;
    myFilesToResolve = filesToResolve;
    myActiveProfiles = activeProfiles;
    myInactiveProfiles = inactiveProfiles;
    myWorkspaceMap = workspaceMap;
    myLocalRepositoryFile = localRepositoryFile;
    myUserProperties = userProperties;
  }

  public ArrayList<MavenServerExecutionResult> resolveProjects() {
    try {
      return doResolveProject();
    }
    catch (Exception e) {
      throw myEmbedder.wrapToSerializableRuntimeException(e);
    }
  }

  private ArrayList<MavenServerExecutionResult> doResolveProject() {
    List<File> files = myFilesToResolve;
    File file = !files.isEmpty() ? files.iterator().next() : null;
    MavenExecutionRequest request = myEmbedder.createRequest(file, myActiveProfiles, myInactiveProfiles, myUserProperties);

    request.setUpdateSnapshots(myUpdateSnapshots);

    ArrayList<MavenServerExecutionResult> executionResults = new ArrayList<>();

    myEmbedder.executeWithMavenSession(request, myWorkspaceMap, session -> {
      executionResults.addAll(getExecutionResults(session, files, request));
    });

    return executionResults;
  }

  private ArrayList<MavenServerExecutionResult> getExecutionResults(MavenSession session,
                                                                     Collection<File> files,
                                                                     MavenExecutionRequest request) {
    ArrayList<MavenServerExecutionResult> executionResults = new ArrayList<>();
    try {
      List<ProjectBuildingResult> buildingResults = getProjectBuildingResults(request, files, session);

      List<Exception> exceptions = new ArrayList<>();
      List<MavenProject> projects = new ArrayList<>();
      for (ProjectBuildingResult result : buildingResults) {
        MavenProject project = result.getProject();
        if (project != null) {
          projects.add(project);
        }
      }
      session.setProjects(projects);
      afterProjectsRead(session, exceptions);

      fillSessionCache(session, session.getRepositorySession(), buildingResults);

      for (ProjectBuildingResult buildingResult : buildingResults) {
        MavenProject project = buildingResult.getProject();
        File pomFile = buildingResult.getPomFile();
        List<ModelProblem> modelProblems = buildingResult.getProblems();

        if (project == null || pomFile == null) {
          executionResults.add(createExecutionResult(pomFile, modelProblems));
          continue;
        }

        executionResults.add(resolveBuildingResult(session.getRepositorySession(), project, modelProblems, exceptions));
      }
    }
    catch (Exception e) {
      executionResults.add(createExecutionResult(e));
    }
    return executionResults;
  }

  private MavenServerExecutionResult resolveBuildingResult(RepositorySystemSession repositorySession,
                                                            MavenProject project,
                                                            List<ModelProblem> modelProblems,
                                                            List<Exception> exceptions) {
    try {
      DependencyResolutionResult dependencyResolutionResult = resolveDependencies(project, repositorySession);
      Set<Artifact> artifacts = resolveArtifacts(project, dependencyResolutionResult);
      project.setArtifacts(artifacts);
      return createExecutionResult(exceptions, modelProblems, project, dependencyResolutionResult);
    }
    catch (Exception e) {
      return createExecutionResult(project, e);
    }
  }

  /**
   * copied from {@link DefaultProjectBuilder#resolveDependencies(MavenProject, RepositorySystemSession)}
   */
  private DependencyResolutionResult resolveDependencies(MavenProject project, RepositorySystemSession session) {
    DependencyResolutionResult resolutionResult;

    try {
      ProjectDependenciesResolver dependencyResolver = myEmbedder.getComponent(ProjectDependenciesResolver.class);
      DefaultDependencyResolutionRequest resolution = new DefaultDependencyResolutionRequest(project, session);
      resolutionResult = dependencyResolver.resolve(resolution);
    }
    catch (DependencyResolutionException e) {
      myEmbedder.warn("Dependency resolution error", e);
      resolutionResult = e.getResult();
    }

    return resolutionResult;
  }

  private MavenServerExecutionResult createExecutionResult(Exception exception) {
    return createExecutionResult(null, exception);
  }

  private MavenServerExecutionResult createExecutionResult(MavenProject mavenProject, Exception exception) {
    return createExecutionResult(Collections.singletonList(exception), Collections.emptyList(), mavenProject, null);
  }

  private MavenServerExecutionResult createExecutionResult(List<Exception> exceptions,
                                                            List<ModelProblem> modelProblems,
                                                            MavenProject mavenProject,
                                                            DependencyResolutionResult dependencyResolutionResult) {
    File file = null == mavenProject ? null : mavenProject.getFile();

    List<Exception> allExceptions = new ArrayList<>(exceptions);
    if (dependencyResolutionResult != null && dependencyResolutionResult.getCollectionErrors() != null) {
      allExceptions.addAll(dependencyResolutionResult.getCollectionErrors());
    }

    Collection<MavenProjectProblem> problems = myEmbedder.collectProblems(file, allExceptions, modelProblems);

    Collection<MavenProjectProblem> unresolvedProblems = new HashSet<>();
    collectUnresolvedArtifactProblems(file, dependencyResolutionResult, unresolvedProblems);

    if (mavenProject == null) return new MavenServerExecutionResult(null, problems, Collections.emptySet());

    MavenModel model = new MavenModel();
    Model nativeModel = mavenProject.getModel();
    try {
      DependencyNode dependencyGraph =
        dependencyResolutionResult != null ? dependencyResolutionResult.getDependencyGraph() : null;

      List<DependencyNode> dependencyNodes = dependencyGraph != null ? dependencyGraph.getChildren() : Collections.emptyList();
      model = Maven40AetherModelConverter.convertModelWithAetherDependencyTree(
        mavenProject,
        nativeModel,
        dependencyNodes,
        myLocalRepositoryFile);
    }
    catch (Exception e) {
      problems.addAll(myEmbedder.collectProblems(mavenProject.getFile(), Collections.singleton(e), modelProblems));
    }

    Map<String, List<String>> injectedProfilesMap = mavenProject.getInjectedProfileIds();

    List<String> activatedProfiles = new ArrayList<>();
    for (List<String> profileList : injectedProfilesMap.values()) {
      activatedProfiles.addAll(profileList);
    }

    Map<String, String> mavenModelMap = Maven40ModelConverter.convertToMap(nativeModel);
    MavenServerExecutionResult.ProjectData data =
      new MavenServerExecutionResult.ProjectData(model, mavenModelMap, null, activatedProfiles);
    if (null == model.getBuild() || null == model.getBuild().getDirectory()) {
      data = null;
    }
    return new MavenServerExecutionResult(data, problems, Collections.emptySet());
  }

  private MavenServerExecutionResult createExecutionResult(File file, List<ModelProblem> modelProblems) {
    Collection<MavenProjectProblem> problems = myEmbedder.collectProblems(file, Collections.emptyList(), modelProblems);
    return new MavenServerExecutionResult(null, problems, Collections.emptySet());
  }

  private void collectUnresolvedArtifactProblems(File file,
                                                  DependencyResolutionResult result,
                                                  Collection<MavenProjectProblem> problems) {
    if (result == null) return;
    String path = file == null ? "" : file.getPath();
    for (Dependency unresolvedDependency : result.getUnresolvedDependencies()) {
      for (Exception exception : result.getResolutionErrors(unresolvedDependency)) {
        String message = Maven40ServerEmbedderImpl.getRootMessage(exception);
        Artifact artifact = RepositoryUtils.toArtifact(unresolvedDependency.getArtifact());
        MavenArtifact mavenArtifact = Maven40ModelConverter.convertArtifact(artifact, myLocalRepositoryFile);
        problems.add(MavenProjectProblem.createUnresolvedArtifactProblem(path, message, false, mavenArtifact));
        break;
      }
    }
  }

  private Set<Artifact> resolveArtifacts(MavenProject project, DependencyResolutionResult dependencyResolutionResult) {
    Set<Artifact> artifacts = new LinkedHashSet<>();
    DependencyNode graph = dependencyResolutionResult.getDependencyGraph();
    if (graph == null || graph.getChildren() == null || graph.getChildren().isEmpty()) return artifacts;
    List<String> projectTrail =
      null == project.getArtifact() ? Collections.emptyList() : Collections.singletonList(project.getArtifact().getId());
    addArtifacts(artifacts, graph.getChildren(), projectTrail);
    return artifacts;
  }

  private static void addArtifacts(Set<Artifact> artifacts, List<DependencyNode> nodes, List<String> parentTrail) {
    for (DependencyNode node : nodes) {
      if (node.getData().get(ConflictResolver.NODE_DATA_WINNER) != null) {
        continue;
      }
      Artifact artifact = RepositoryUtils.toArtifact(node.getDependency());
      if (artifact == null) {
        continue;
      }
      List<String> nodeTrail = new ArrayList<>(parentTrail.size() + 1);
      nodeTrail.addAll(parentTrail);
      nodeTrail.add(artifact.getId());
      artifact.setDependencyTrail(nodeTrail);
      artifacts.add(artifact);
      addArtifacts(artifacts, node.getChildren(), parentTrail);
    }
  }

  private static void fillSessionCache(MavenSession mavenSession,
                                        RepositorySystemSession session,
                                        List<ProjectBuildingResult> buildingResults) {
    int initialCapacity = (int)(buildingResults.size() * 1.5);
    Map<MavenId, org.apache.maven.api.model.Model> cacheMavenModelMap = new HashMap<>(initialCapacity);
    Map<String, MavenProject> mavenProjectMap = new HashMap<>(initialCapacity);
    for (ProjectBuildingResult result : buildingResults) {
      if (result.getProject() == null) continue;
      if (result.getProblems() != null && !result.getProblems().isEmpty()) continue;
      org.apache.maven.api.model.Model model = result.getProject().getModel().getDelegate();
      String key = ArtifactUtils.key(model.getGroupId(), model.getArtifactId(), model.getVersion());
      mavenProjectMap.put(key, result.getProject());
      cacheMavenModelMap.put(new MavenId(model.getGroupId(), model.getArtifactId(), model.getVersion()), model);
    }
    mavenSession.setProjectMap(mavenProjectMap);
    Maven40WorkspaceMapReader maven40WorkspaceMapReader = null;
    WorkspaceReader reader = session.getWorkspaceReader();
    if (reader instanceof Maven40WorkspaceMapReader) {
      maven40WorkspaceMapReader = (Maven40WorkspaceMapReader)reader;
    }
    else if (reader instanceof MavenChainedWorkspaceReader) {
      for (WorkspaceReader chainedReader : ((MavenChainedWorkspaceReader)reader).getReaders()) {
        if (chainedReader instanceof Maven40WorkspaceMapReader) {
          maven40WorkspaceMapReader = (Maven40WorkspaceMapReader)chainedReader;
          break;
        }
      }
    }
    if (null != maven40WorkspaceMapReader) {
      maven40WorkspaceMapReader.setCacheModelMap(cacheMavenModelMap);
    }
  }

  /**
   * adapted from {@link DefaultMaven#afterProjectsRead(MavenSession)}
   */
  private void afterProjectsRead(MavenSession session, List<Exception> exceptions) {
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    Collection<AbstractMavenLifecycleParticipant> lifecycleParticipants =
      myEmbedder.getExtensionComponents(Collections.emptyList(), AbstractMavenLifecycleParticipant.class);
    for (AbstractMavenLifecycleParticipant listener : lifecycleParticipants) {
      Thread.currentThread().setContextClassLoader(listener.getClass().getClassLoader());
      try {
        listener.afterProjectsRead(session);
      }
      catch (Exception e) {
        exceptions.add(e);
      }
      finally {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
      }
    }
  }

  private List<ProjectBuildingResult> getProjectBuildingResults(MavenExecutionRequest request,
                                                                 Collection<File> files,
                                                                 MavenSession session) {
    ProjectBuilder builder = myEmbedder.getComponent(ProjectBuilder.class);

    List<ProjectBuildingResult> buildingResults = new ArrayList<>();

    ProjectBuildingRequest projectBuildingRequest = request.getProjectBuildingRequest();
    projectBuildingRequest.setRepositorySession(session.getRepositorySession());
    projectBuildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_STRICT);
    projectBuildingRequest.setActiveProfileIds(request.getActiveProfiles());
    projectBuildingRequest.setInactiveProfileIds(request.getInactiveProfiles());
    projectBuildingRequest.setResolveDependencies(false);

    buildSinglePom(builder, buildingResults, projectBuildingRequest, request.getPom());

    Set<File> processedFiles = new HashSet<>();
    for (ProjectBuildingResult buildingResult : buildingResults) {
      processedFiles.add(buildingResult.getPomFile());
    }
    Set<File> nonProcessedFiles = new HashSet<>(files);
    nonProcessedFiles.removeAll(processedFiles);
    for (File file : nonProcessedFiles) {
      buildSinglePom(builder, buildingResults, projectBuildingRequest, file);
    }

    return buildingResults;
  }

  private static void buildSinglePom(ProjectBuilder builder,
                                      List<ProjectBuildingResult> buildingResults,
                                      ProjectBuildingRequest projectBuildingRequest,
                                      File pomFile) {
    try {
      List<ProjectBuildingResult> build = builder.build(Collections.singletonList(pomFile), true, projectBuildingRequest);
      buildingResults.addAll(build);
    }
    catch (ProjectBuildingException e) {
      Maven40ResolverUtil.handleProjectBuildingException(buildingResults, e);
    }
  }
}
