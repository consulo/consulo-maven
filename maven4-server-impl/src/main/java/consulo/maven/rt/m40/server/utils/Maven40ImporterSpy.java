// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
package consulo.maven.rt.m40.server.utils;

import consulo.maven.rt.server.common.server.MavenServerConsoleIndicator;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.Artifact;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Named("Intellij Idea Maven 4 Importer Spy")
@Singleton
public class Maven40ImporterSpy extends AbstractEventSpy {

  private volatile MavenServerConsoleIndicator myIndicator;
  private final Set<String> downloadedArtifacts = Collections.newSetFromMap(new ConcurrentHashMap<>());

  @Override
  public void onEvent(Object o) throws Exception {
    if (!(o instanceof RepositoryEvent)) return;
    RepositoryEvent event = (RepositoryEvent)o;
    if (event.getArtifact() == null) {
      return;
    }

    MavenServerConsoleIndicator indicator = myIndicator;
    if (indicator == null) {
      return;
    }

    if (event.getType() == RepositoryEvent.EventType.ARTIFACT_DOWNLOADING) {
      String dependencyId = toString(event.getArtifact());
      indicator.startedDownload(MavenServerConsoleIndicator.ResolveType.DEPENDENCY, dependencyId);
      downloadedArtifacts.add(dependencyId);
    }
    else if (event.getType() == RepositoryEvent.EventType.ARTIFACT_RESOLVED) {
      String dependencyId = toString(event.getArtifact());
      if (downloadedArtifacts.remove(dependencyId)) {
        processResolvedArtifact(event, indicator, dependencyId);
      }
    }
  }

  private static void processResolvedArtifact(RepositoryEvent event,
                                               MavenServerConsoleIndicator indicator,
                                               String dependencyId) throws Exception {
    if (event.getExceptions() != null && !event.getExceptions().isEmpty()) {
      StringBuilder builder = new StringBuilder();
      for (Exception e : event.getExceptions()) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        builder.append(sw).append("\n");
      }
      Exception mainException = event.getException();
      indicator.failedDownload(MavenServerConsoleIndicator.ResolveType.DEPENDENCY, dependencyId,
                               mainException != null ? mainException.getMessage() : "", builder.toString());
    }
    else {
      indicator.completedDownload(MavenServerConsoleIndicator.ResolveType.DEPENDENCY, dependencyId);
    }
  }

  @Override
  public void close() {
    downloadedArtifacts.clear();
  }

  private static String toString(Artifact artifact) {
    return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getClassifier() + ":" + artifact.getVersion();
  }

  public void setIndicator(MavenServerConsoleIndicator indicator) {
    myIndicator = indicator;
  }
}
