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
package consulo.maven.rt.m2.server;

import consulo.maven.rt.m2.server.embedder.Maven2ServerEmbedderImpl;
import consulo.maven.rt.m2.server.embedder.Maven2ServerIndexerImpl;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.maven.rt.server.common.model.MavenModel;
import consulo.maven.rt.server.common.server.*;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;

public class Maven2ServerImpl extends MavenRemoteObject implements MavenServer
{
	@Override
	public void set(MavenServerLogger logger, MavenServerDownloadListener downloadListener) throws RemoteException
	{
		try
		{
			Maven2ServerGlobals.set(logger, downloadListener);
		}
		catch(Exception e)
		{
			throw rethrowException(e);
		}
	}

	@Override
	public MavenServerEmbedder createEmbedder(MavenServerSettings settings) throws RemoteException
	{
		try
		{
			Maven2ServerEmbedderImpl result = Maven2ServerEmbedderImpl.create(settings);
			UnicastRemoteObject.exportObject(result, 0);
			return result;
		}
		catch(RemoteException e)
		{
			throw rethrowException(e);
		}
	}

	@Override
	public MavenServerIndexer createIndexer() throws RemoteException
	{
		try
		{
			Maven2ServerIndexerImpl result = new Maven2ServerIndexerImpl();
			UnicastRemoteObject.exportObject(result, 0);
			return result;
		}
		catch(RemoteException e)
		{
			throw rethrowException(e);
		}
	}

	@Override
	public MavenModel interpolateAndAlignModel(MavenModel model, File basedir)
	{
		try
		{
			return Maven2ServerEmbedderImpl.interpolateAndAlignModel(model, basedir);
		}
		catch(Exception e)
		{
			throw rethrowException(e);
		}
	}

	@Override
	public MavenModel assembleInheritance(MavenModel model, MavenModel parentModel)
	{
		try
		{
			return Maven2ServerEmbedderImpl.assembleInheritance(model, parentModel);
		}
		catch(Exception e)
		{
			throw rethrowException(e);
		}
	}

	@Override
	public ProfileApplicationResult applyProfiles(MavenModel model, File basedir, MavenExplicitProfiles explicitProfiles, Collection<String> alwaysOnProfiles)
	{
		try
		{
			return Maven2ServerEmbedderImpl.applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
		}
		catch(Exception e)
		{
			throw rethrowException(e);
		}
	}

	@Override
	public synchronized void unreferenced()
	{
		System.exit(0);
	}
}
