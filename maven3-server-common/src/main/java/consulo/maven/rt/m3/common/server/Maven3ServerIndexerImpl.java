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
package consulo.maven.rt.m3.common.server;

import consulo.maven.rt.server.common.model.MavenArchetype;
import consulo.maven.rt.server.common.model.MavenArtifactInfo;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.maven.rt.server.common.server.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.archetype.source.ArchetypeDataSource;
import org.apache.maven.archetype.source.ArchetypeDataSourceException;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.wagon.events.TransferEvent;
import org.sonatype.nexus.index.*;
import org.sonatype.nexus.index.context.IndexUtils;
import org.sonatype.nexus.index.context.IndexingContext;
import org.sonatype.nexus.index.creator.JarFileContentsIndexCreator;
import org.sonatype.nexus.index.creator.MinimalArtifactInfoIndexCreator;
import org.sonatype.nexus.index.updater.IndexUpdateRequest;
import org.sonatype.nexus.index.updater.IndexUpdater;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.*;

public abstract class Maven3ServerIndexerImpl extends MavenRemoteObject implements MavenServerIndexer
{
	private Maven3ServerEmbedder myEmbedder;
	private final NexusIndexer myIndexer;
	private final IndexUpdater myUpdater;
	private final ArtifactContextProducer myArtifactContextProducer;

	private final Map<Integer, IndexingContext> myIndices = new HashMap<Integer, IndexingContext>();

	public Maven3ServerIndexerImpl(Maven3ServerEmbedder embedder) throws RemoteException
	{
		myEmbedder = embedder;

		myIndexer = myEmbedder.getComponent(NexusIndexer.class);
		myUpdater = myEmbedder.getComponent(IndexUpdater.class);
		myArtifactContextProducer = myEmbedder.getComponent(ArtifactContextProducer.class);

		MavenServerUtil.registerShutdownTask(new Runnable()
		{
			@Override
			public void run()
			{
				release();
			}
		});
	}

	@Override
	public int createIndex(@Nonnull String indexId, @Nonnull String repositoryId, @Nullable File file, @Nullable String url, @Nonnull File indexDir) throws RemoteException,
			MavenServerIndexerException
	{
		try
		{

			IndexingContext context = myIndexer.addIndexingContextForced(indexId, repositoryId, file, indexDir, url, null, // repo update url
					Arrays.asList(new MinimalArtifactInfoIndexCreator(), new JarFileContentsIndexCreator()));
			int id = System.identityHashCode(context);
			myIndices.put(id, context);
			return id;
		}
		catch(Exception e)
		{
			throw new MavenServerIndexerException(wrapException(e));
		}
	}

	@Override
	public void releaseIndex(int id) throws RemoteException, MavenServerIndexerException
	{
		try
		{
			myIndexer.removeIndexingContext(getIndex(id), false);
		}
		catch(Exception e)
		{
			throw new MavenServerIndexerException(wrapException(e));
		}
	}

	@Nonnull
	private IndexingContext getIndex(int id)
	{
		IndexingContext index = myIndices.get(id);
		if(index == null)
		{
			throw new RuntimeException("Index not found for id: " + id);
		}
		return index;
	}

	@Override
	public boolean indexExists(File dir) throws RemoteException
	{
		try
		{
			return IndexReader.indexExists(dir);
		}
		catch(Exception e)
		{
			Maven3ServerGlobals.getLogger().warn(e);
		}
		return false;
	}

	@Override
	public int getIndexCount() throws RemoteException
	{
		return myIndexer.getIndexingContexts().size();
	}

	private String getRepositoryPathOrUrl(IndexingContext index)
	{
		File file = index.getRepository();
		return file == null ? index.getRepositoryUrl() : file.getPath();
	}

	@Override
	public void updateIndex(int id, MavenServerSettings settings, final MavenServerProgressIndicator indicator) throws RemoteException, MavenServerIndexerException,
			MavenServerProcessCanceledException
	{
		final IndexingContext index = getIndex(id);

		try
		{
			File repository = index.getRepository();
			if(repository != null)
			{ // is local repository
				if(repository.exists())
				{
					indicator.setIndeterminate(true);
					try
					{
						myIndexer.scan(index, new MyScanningListener(indicator), false);
					}
					finally
					{
						indicator.setIndeterminate(false);
					}
				}
			}
			else
			{
				final Maven3ServerEmbedder embedder = createEmbedder(settings);

				MavenExecutionRequest r = embedder.createRequest(null, Collections.<String>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList());

				final IndexUpdateRequest request = new IndexUpdateRequest(index);

				try
				{
					embedder.executeWithMavenSession(r, new Runnable()
					{
						@Override
						public void run()
						{
							request.setResourceFetcher(new Maven3ServerIndexFetcher(index.getRepositoryId(), index.getRepositoryUrl(), embedder.getComponent(WagonManager.class), embedder
									.getComponent(RepositorySystem.class), new WagonTransferListenerAdapter(indicator)
							{
								@Override
								protected void downloadProgress(long downloaded, long total)
								{
									super.downloadProgress(downloaded, total);
									try
									{
										myIndicator.setFraction(((double) downloaded) / total);
									}
									catch(RemoteException e)
									{
										throw new RuntimeRemoteException(e);
									}
								}

								@Override
								public void transferCompleted(TransferEvent event)
								{
									super.transferCompleted(event);
									try
									{
										myIndicator.setText2("Processing indices...");
									}
									catch(RemoteException e)
									{
										throw new RuntimeRemoteException(e);
									}
								}
							}));
							try
							{
								myUpdater.fetchAndUpdateIndex(request);
							}
							catch(IOException e)
							{
								throw new RuntimeException(e);
							}
						}
					});
				}
				finally
				{
					embedder.release();
				}
			}
		}
		catch(RuntimeRemoteException e)
		{
			throw e.getCause();
		}
		catch(MavenProcessCanceledRuntimeException e)
		{
			throw new MavenServerProcessCanceledException();
		}
		catch(Exception e)
		{
			throw new MavenServerIndexerException(wrapException(e));
		}
	}

	public abstract Maven3ServerEmbedder createEmbedder(MavenServerSettings settings) throws RemoteException;


	@Override
	public void processArtifacts(int indexId, MavenServerIndicesProcessor processor) throws RemoteException, MavenServerIndexerException
	{
		try
		{
			final int CHUNK_SIZE = 10000;

			IndexReader r = getIndex(indexId).getIndexReader();
			int total = r.numDocs();

			List<IndexedMavenId> result = new ArrayList<IndexedMavenId>(Math.min(CHUNK_SIZE, total));
			for(int i = 0; i < total; i++)
			{
				if(r.isDeleted(i))
				{
					continue;
				}

				Document doc = r.document(i);
				String uinfo = doc.get(SEARCH_TERM_COORDINATES);
				if(uinfo == null)
				{
					continue;
				}

				String[] uInfoParts = uinfo.split("\\|");
				if(uInfoParts.length < 3)
				{
					continue;
				}

				String groupId = uInfoParts[0];
				String artifactId = uInfoParts[1];
				String version = uInfoParts[2];

				if(groupId == null || artifactId == null || version == null)
				{
					continue;
				}

				String packaging = doc.get(ArtifactInfo.PACKAGING);
				String description = doc.get(ArtifactInfo.DESCRIPTION);

				result.add(new IndexedMavenId(groupId, artifactId, version, packaging, description));

				if(result.size() == CHUNK_SIZE)
				{
					processor.processArtifacts(result);
					result.clear();
				}
			}

			if(!result.isEmpty())
			{
				processor.processArtifacts(result);
			}
		}
		catch(Exception e)
		{
			throw new MavenServerIndexerException(wrapException(e));
		}
	}

	@Override
	public MavenId addArtifact(int indexId, File artifactFile) throws RemoteException, MavenServerIndexerException
	{
		try
		{
			IndexingContext index = getIndex(indexId);
			ArtifactContext artifactContext = myArtifactContextProducer.getArtifactContext(index, artifactFile);
			if(artifactContext == null)
			{
				return null;
			}

			addArtifact(myIndexer, index, artifactContext);

			ArtifactInfo a = artifactContext.getArtifactInfo();
			return new MavenId(a.groupId, a.artifactId, a.version);
		}
		catch(Exception e)
		{
			throw new MavenServerIndexerException(wrapException(e));
		}
	}

	public static void addArtifact(NexusIndexer indexer,
								   IndexingContext index,
								   ArtifactContext artifactContext) throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException
	{
		indexer.addArtifactToIndex(artifactContext, index);
		// this hack is necessary to invalidate searcher's and reader's cache (may not be required then lucene or nexus library change
		Method m = index.getClass().getDeclaredMethod("closeReaders");
		m.setAccessible(true);
		m.invoke(index);
	}


	@Override
	public Set<MavenArtifactInfo> search(int indexId, Query query, int maxResult) throws RemoteException, MavenServerIndexerException
	{
		try
		{
			IndexingContext index = getIndex(indexId);

			TopDocs docs = null;
			try
			{
				BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);
				docs = index.getIndexSearcher().search(query, null, maxResult);
			}
			catch(BooleanQuery.TooManyClauses ignore)
			{
				// this exception occurs when too wide wildcard is used on too big data.
			}

			if(docs == null || docs.scoreDocs.length == 0)
			{
				return Collections.emptySet();
			}

			Set<MavenArtifactInfo> result = new HashSet<MavenArtifactInfo>();

			for(int i = 0; i < docs.scoreDocs.length; i++)
			{
				int docIndex = docs.scoreDocs[i].doc;
				Document doc = index.getIndexReader().document(docIndex);
				ArtifactInfo a = IndexUtils.constructArtifactInfo(doc, index);
				if(a == null)
				{
					continue;
				}

				a.repository = getRepositoryPathOrUrl(index);
				result.add(MavenModelConverter.convertArtifactInfo(a));
			}
			return result;
		}
		catch(Exception e)
		{
			throw new MavenServerIndexerException(wrapException(e));
		}
	}

	@Override
	public Collection<MavenArchetype> getArchetypes() throws RemoteException
	{
		Set<MavenArchetype> result = new HashSet<MavenArchetype>();
		doCollectArchetypes("internal-catalog", result);
		doCollectArchetypes("nexus", result);
		return result;
	}

	private void doCollectArchetypes(String roleHint, Set<MavenArchetype> result) throws RemoteException
	{
		try
		{
			ArchetypeDataSource source = myEmbedder.getComponent(ArchetypeDataSource.class, roleHint);
			ArchetypeCatalog archetypeCatalog = source.getArchetypeCatalog(new Properties());

			for(Object each : archetypeCatalog.getArchetypes())
			{
				result.add(MavenModelConverter.convertArchetype((Archetype) each));
			}
		}
		catch(ArchetypeDataSourceException e)
		{
			Maven3ServerGlobals.getLogger().warn(e);
		}
	}

	@Override
	public void release()
	{
		try
		{
			myEmbedder.release();
		}
		catch(Exception e)
		{
			throw rethrowException(e);
		}
	}

	private static class MyScanningListener implements ArtifactScanningListener
	{
		private final MavenServerProgressIndicator p;

		public MyScanningListener(MavenServerProgressIndicator indicator)
		{
			p = indicator;
		}

		public void scanningStarted(IndexingContext ctx)
		{
			try
			{
				if(p.isCanceled())
				{
					throw new MavenProcessCanceledRuntimeException();
				}
			}
			catch(RemoteException e)
			{
				throw new RuntimeRemoteException(e);
			}
		}

		public void scanningFinished(IndexingContext ctx, ScanningResult result)
		{
			try
			{
				if(p.isCanceled())
				{
					throw new MavenProcessCanceledRuntimeException();
				}
			}
			catch(RemoteException e)
			{
				throw new RuntimeRemoteException(e);
			}
		}

		public void artifactError(ArtifactContext ac, Exception e)
		{
		}

		public void artifactDiscovered(ArtifactContext ac)
		{
			try
			{
				if(p.isCanceled())
				{
					throw new MavenProcessCanceledRuntimeException();
				}
				ArtifactInfo info = ac.getArtifactInfo();
				p.setText2(info.groupId + ":" + info.artifactId + ":" + info.version);
			}
			catch(RemoteException e)
			{
				throw new RuntimeRemoteException(e);
			}
		}
	}
}
