/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.maven.rt.server.common.server;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.Nonnull;

import consulo.maven.rt.server.common.model.MavenId;
import consulo.maven.rt.server.common.model.MavenModel;
import consulo.maven.rt.server.common.model.MavenProjectProblem;
import jakarta.annotation.Nullable;

public class MavenServerExecutionResult implements Serializable
{
	@Nullable
	public final ProjectData projectData;
	@Nonnull
	public final Collection<MavenProjectProblem> problems;
	@Nonnull
	public final Set<MavenId> unresolvedArtifacts;

	public MavenServerExecutionResult(ProjectData projectData, Collection<MavenProjectProblem> problems, Set<MavenId> unresolvedArtifacts)
	{
		this.projectData = projectData;
		this.problems = problems;
		this.unresolvedArtifacts = unresolvedArtifacts;
	}

	public static class ProjectData implements Serializable
	{
		public final MavenModel mavenModel;
		public final Map<String, String> mavenModelMap;
		public final NativeMavenProjectHolder nativeMavenProject;
		public final Collection<String> activatedProfiles;

		public ProjectData(MavenModel mavenModel, Map<String, String> mavenModelMap, NativeMavenProjectHolder nativeMavenProject, Collection<String> activatedProfiles)
		{
			this.mavenModel = mavenModel;
			this.mavenModelMap = mavenModelMap;
			this.nativeMavenProject = nativeMavenProject;
			this.activatedProfiles = activatedProfiles;
		}
	}
}
