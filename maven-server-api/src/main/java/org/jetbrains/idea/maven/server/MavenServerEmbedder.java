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
package org.jetbrains.idea.maven.server;

import java.io.File;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;

public interface MavenServerEmbedder extends Remote
{
	String MAVEN_EMBEDDER_VERSION = "idea.maven.embedder.version";
	String MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS = "idea.maven.embedder.ext.cli.args";

	void customize(@javax.annotation.Nullable MavenWorkspaceMap workspaceMap,
			boolean failOnUnresolvedDependency,
			@Nonnull MavenServerConsole console,
			@Nonnull MavenServerProgressIndicator indicator,
			boolean alwaysUpdateSnapshots) throws RemoteException;

	void customizeComponents() throws RemoteException;

	@Nonnull
	List<String> retrieveAvailableVersions(@Nonnull String groupId, @Nonnull String artifactId, @Nonnull List<MavenRemoteRepository> remoteRepositories) throws RemoteException;


	@Nonnull
	MavenServerExecutionResult resolveProject(@Nonnull File file,
			@Nonnull Collection<String> activeProfiles,
			@Nonnull Collection<String> inactiveProfiles) throws RemoteException, MavenServerProcessCanceledException;

	@javax.annotation.Nullable
	String evaluateEffectivePom(@Nonnull File file, @Nonnull List<String> activeProfiles, @Nonnull List<String> inactiveProfiles) throws RemoteException, MavenServerProcessCanceledException;

	@Nonnull
	MavenArtifact resolve(@Nonnull MavenArtifactInfo info, @Nonnull List<MavenRemoteRepository> remoteRepositories) throws RemoteException, MavenServerProcessCanceledException;

	@Nonnull
	List<MavenArtifact> resolveTransitively(@Nonnull List<MavenArtifactInfo> artifacts,
			@Nonnull List<MavenRemoteRepository> remoteRepositories) throws RemoteException, MavenServerProcessCanceledException;

	Collection<MavenArtifact> resolvePlugin(@Nonnull MavenPlugin plugin,
			@Nonnull List<MavenRemoteRepository> repositories,
			int nativeMavenProjectId,
			boolean transitive) throws RemoteException, MavenServerProcessCanceledException;

	@Nonnull
	MavenServerExecutionResult execute(@Nonnull File file,
			@Nonnull Collection<String> activeProfiles,
			@Nonnull Collection<String> inactiveProfiles,
			@Nonnull List<String> goals,
			@Nonnull final List<String> selectedProjects,
			boolean alsoMake,
			boolean alsoMakeDependents) throws RemoteException, MavenServerProcessCanceledException;

	void reset() throws RemoteException;

	void release() throws RemoteException;

	void clearCaches() throws RemoteException;

	void clearCachesFor(MavenId projectId) throws RemoteException;
}
