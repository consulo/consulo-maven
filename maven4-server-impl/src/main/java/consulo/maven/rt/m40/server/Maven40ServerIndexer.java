package consulo.maven.rt.m40.server;

import consulo.maven.rt.server.common.model.MavenArchetype;
import consulo.maven.rt.server.common.model.MavenArtifactInfo;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.maven.rt.server.common.server.MavenRemoteObject;
import consulo.maven.rt.server.common.server.MavenServerIndexer;
import consulo.maven.rt.server.common.server.MavenServerIndexerException;
import consulo.maven.rt.server.common.server.MavenServerIndicesProcessor;
import consulo.maven.rt.server.common.server.MavenServerProgressIndicator;
import consulo.maven.rt.server.common.server.MavenServerSettings;
import org.apache.lucene.search.Query;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * No-op indexer for Maven 4. The Nexus-based local repository indexer is not available in Maven 4.
 */
class Maven40ServerIndexer extends MavenRemoteObject implements MavenServerIndexer {

  @Override
  public int createIndex(String indexId, String repositoryId, @Nullable File file, @Nullable String url, File indexDir)
    throws RemoteException, MavenServerIndexerException {
    return 0;
  }

  @Override
  public void releaseIndex(int id) throws RemoteException, MavenServerIndexerException {
  }

  @Override
  public int getIndexCount() throws RemoteException {
    return 0;
  }

  @Override
  public void updateIndex(int id, MavenServerSettings settings, MavenServerProgressIndicator indicator)
    throws RemoteException, MavenServerIndexerException {
  }

  @Override
  public void processArtifacts(int indexId, MavenServerIndicesProcessor processor)
    throws RemoteException, MavenServerIndexerException {
  }

  @Override
  public @Nullable MavenId addArtifact(int indexId, File artifactFile)
    throws RemoteException, MavenServerIndexerException {
    return null;
  }

  @Override
  public Set<MavenArtifactInfo> search(int indexId, Query query, int maxResult)
    throws RemoteException, MavenServerIndexerException {
    return Collections.emptySet();
  }

  @Override
  public Collection<MavenArchetype> getArchetypes() throws RemoteException {
    return Collections.emptyList();
  }

  @Override
  public void release() throws RemoteException {
  }

  @Override
  public boolean indexExists(File dir) throws RemoteException {
    return false;
  }
}
