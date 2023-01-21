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
package consulo.maven.rt.m3.common.server;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.execution.MavenExecutionRequest;
import consulo.maven.rt.server.common.model.MavenRemoteRepository;
import consulo.maven.rt.server.common.server.MavenRemoteObject;
import consulo.maven.rt.server.common.server.MavenServerEmbedder;
import consulo.maven.rt.server.common.server.MavenServerSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * @author Vladislav.Soroka
 * @since 1/20/2015
 */
public abstract class Maven3ServerEmbedder extends MavenRemoteObject implements MavenServerEmbedder
{
	public final static boolean USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING = System.getProperty("idea.maven3.use.compat.resolver") != null;
	private final static String MAVEN_VERSION = System.getProperty(MAVEN_EMBEDDER_VERSION);

	protected Maven3ServerEmbedder(MavenServerSettings settings)
	{
	}

	protected abstract ArtifactRepository getLocalRepository();

	@Nonnull
	@Override
	public List<String> retrieveAvailableVersions(@Nonnull String groupId, @Nonnull String artifactId, @Nonnull List<MavenRemoteRepository> remoteRepositories) throws RemoteException
	{
		try
		{
			Artifact artifact = new DefaultArtifact(groupId, artifactId, "", Artifact.SCOPE_COMPILE, "pom", null, new DefaultArtifactHandler("pom"));
			List<ArtifactVersion> versions = getComponent(ArtifactMetadataSource.class).retrieveAvailableVersions(artifact, getLocalRepository(), convertRepositories(remoteRepositories));

			List<String> mapToStringVersions = new ArrayList<String>(versions.size());
			for(ArtifactVersion version : versions)
			{
				mapToStringVersions.add(version.toString());
			}
			return mapToStringVersions;
		}
		catch(Exception e)
		{
			Maven3ServerGlobals.getLogger().info(e);
		}
		return Collections.emptyList();
	}

	@Nonnull
	protected abstract List<ArtifactRepository> convertRepositories(List<MavenRemoteRepository> repositories) throws RemoteException;

	@Nullable
	public String getMavenVersion()
	{
		return MAVEN_VERSION;
	}

	@SuppressWarnings({"unchecked"})
	public abstract <T> T getComponent(Class<T> clazz, String roleHint);

	@SuppressWarnings({"unchecked"})
	public abstract <T> T getComponent(Class<T> clazz);

	public abstract void executeWithMavenSession(MavenExecutionRequest request, Runnable runnable);

	public abstract MavenExecutionRequest createRequest(File file, List<String> activeProfiles, List<String> inactiveProfiles, List<String> goals) throws RemoteException;
}
