// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.maven.rt.m40.server;

import consulo.maven.rt.server.common.server.DownloadArtifactEvent;
import consulo.maven.rt.server.common.server.MavenArtifactEvent;
import consulo.maven.rt.server.common.server.MavenServerConsole;
import consulo.maven.rt.server.common.server.MavenServerConsoleIndicator;
import consulo.maven.rt.server.common.server.MavenServerConsoleEvent;

import java.rmi.RemoteException;
import java.util.Collections;
import java.util.List;

/**
 * Server-side MavenServerConsoleIndicator that forwards download events
 * to the plugin via the existing MavenServerConsole RMI connection.
 */
class Maven40LocalConsoleIndicator implements MavenServerConsoleIndicator {

  private final MavenServerConsole myConsole;

  Maven40LocalConsoleIndicator(MavenServerConsole console) {
    myConsole = console;
  }

  @Override
  public void startedDownload(ResolveType type, String dependencyId) {
    try {
      myConsole.printMessage(LEVEL_INFO, "Downloading: " + dependencyId, null);
    }
    catch (RemoteException ignored) {
    }
  }

  @Override
  public void completedDownload(ResolveType type, String dependencyId) {
    try {
      myConsole.printMessage(LEVEL_INFO, "Downloaded: " + dependencyId, null);
    }
    catch (RemoteException ignored) {
    }
  }

  @Override
  public void failedDownload(ResolveType type, String dependencyId, String errorMessage, String stackTrace) {
    try {
      myConsole.printMessage(LEVEL_WARN, "Failed to download " + dependencyId + ": " + errorMessage, null);
    }
    catch (RemoteException ignored) {
    }
  }

  @Override
  public boolean isCanceled() {
    return false;
  }

  @Override
  public List<MavenArtifactEvent> pullDownloadEvents() {
    return Collections.emptyList();
  }

  @Override
  public List<DownloadArtifactEvent> pullDownloadArtifactEvents() {
    return Collections.emptyList();
  }

  @Override
  public List<MavenServerConsoleEvent> pullConsoleEvents() {
    return Collections.emptyList();
  }

  @Override
  public void cancel() {
  }
}
