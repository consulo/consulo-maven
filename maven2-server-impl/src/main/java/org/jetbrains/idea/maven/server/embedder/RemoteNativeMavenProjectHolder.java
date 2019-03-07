package org.jetbrains.idea.maven.server.embedder;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;

public class RemoteNativeMavenProjectHolder implements NativeMavenProjectHolder
{
	private static final Map<Integer, Reference<RemoteNativeMavenProjectHolder>> myMap = new HashMap<Integer, Reference<RemoteNativeMavenProjectHolder>>();

	private final MavenProject myMavenProject;

	public RemoteNativeMavenProjectHolder(@Nonnull MavenProject mavenProject)
	{
		myMavenProject = mavenProject;
		myMap.put(getId(), new WeakReference<RemoteNativeMavenProjectHolder>(this));
	}

	@Override
	public int getId()
	{
		return System.identityHashCode(this);
	}

	@Nonnull
	public static MavenProject findProjectById(int id)
	{
		Reference<RemoteNativeMavenProjectHolder> reference = myMap.get(id);
		RemoteNativeMavenProjectHolder result = reference == null ? null : reference.get();
		if(result == null)
		{
			throw new RuntimeException("NativeMavenProjectHolder not found for id: " + id);
		}
		return result.myMavenProject;
	}
}
