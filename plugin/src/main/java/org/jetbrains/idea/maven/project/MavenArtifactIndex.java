package org.jetbrains.idea.maven.project;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.util.collection.SmartList;
import consulo.util.lang.Comparing;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import consulo.maven.rt.server.common.model.MavenArtifact;
import consulo.maven.rt.server.common.model.MavenId;

/**
 * @author Sergey Evdokimov
 */
public class MavenArtifactIndex
{

	private static final MavenArtifactIndex EMPTY_INDEX = new MavenArtifactIndex(Collections.<String, Map<String, List<MavenArtifact>>>emptyMap());

	private final Map<String, Map<String, List<MavenArtifact>>> myData;

	private MavenArtifactIndex(Map<String, Map<String, List<MavenArtifact>>> data)
	{
		myData = data;
	}

	public Map<String, Map<String, List<MavenArtifact>>> getData()
	{
		return myData;
	}

	@Nonnull
	public List<MavenArtifact> findArtifacts(@Nullable String groupId, @Nullable String artifactId)
	{
		Map<String, List<MavenArtifact>> groupMap = myData.get(groupId);
		if(groupMap == null)
		{
			return Collections.emptyList();
		}

		List<MavenArtifact> res = groupMap.get(artifactId);
		return res == null ? Collections.<MavenArtifact>emptyList() : res;
	}

	@Nonnull
	public List<MavenArtifact> findArtifacts(@Nullable MavenId mavenId)
	{
		if(mavenId == null)
		{
			return Collections.emptyList();
		}

		return findArtifacts(mavenId.getGroupId(), mavenId.getArtifactId(), mavenId.getVersion());
	}

	@Nullable
	public MavenArtifact findArtifacts(@Nonnull DependencyConflictId id)
	{
		for(MavenArtifact artifact : findArtifacts(id.getGroupId(), id.getArtifactId()))
		{
			if(id.equals(DependencyConflictId.create(artifact)))
			{
				return artifact;
			}
		}

		return null;
	}

	@Nonnull
	public List<MavenArtifact> findArtifacts(@Nullable String groupId, @Nullable String artifactId, @Nullable String version)
	{
		Map<String, List<MavenArtifact>> groupMap = myData.get(groupId);
		if(groupMap == null)
		{
			return Collections.emptyList();
		}

		List<MavenArtifact> artifacts = groupMap.get(artifactId);
		if(artifacts == null)
		{
			return Collections.emptyList();
		}

		List<MavenArtifact> res = new SmartList<MavenArtifact>();
		for(MavenArtifact artifact : artifacts)
		{
			if(Comparing.equal(version, artifact.getVersion()))
			{
				res.add(artifact);
			}
		}

		return res;
	}


	public static MavenArtifactIndex build(@Nonnull List<MavenArtifact> dependencies)
	{
		if(dependencies.isEmpty())
		{
			return EMPTY_INDEX;
		}

		Map<String, Map<String, List<MavenArtifact>>> map = new HashMap<String, Map<String, List<MavenArtifact>>>();

		for(MavenArtifact dep : dependencies)
		{
			String groupId = dep.getGroupId();
			if(groupId == null)
			{
				continue;
			}

			String artifactId = dep.getArtifactId();
			if(artifactId == null)
			{
				continue;
			}

			Map<String, List<MavenArtifact>> groupMap = map.get(groupId);
			if(groupMap == null)
			{
				groupMap = new HashMap<String, List<MavenArtifact>>();
				map.put(groupId, groupMap);
			}

			List<MavenArtifact> artifactList = groupMap.get(artifactId);
			if(artifactList == null)
			{
				artifactList = new SmartList<MavenArtifact>();
				groupMap.put(artifactId, artifactList);
			}

			artifactList.add(dep);
		}

		return new MavenArtifactIndex(map);
	}
}
