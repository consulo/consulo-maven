// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.maven.rt.m40.server.utils;

import consulo.maven.rt.server.common.server.MavenProcessCanceledRuntimeException;
import consulo.maven.rt.server.common.server.MavenServerConsoleIndicator;
import consulo.maven.rt.server.common.server.MavenServerProgressIndicator;
import consulo.maven.rt.server.common.util.MavenStringUtil;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transfer.TransferResource;

import java.io.File;
import java.rmi.RemoteException;

public class Maven40TransferListenerAdapter implements TransferListener {

  protected final MavenServerProgressIndicator myProgressIndicator;
  protected final MavenServerConsoleIndicator myConsoleIndicator;
  protected final Maven40ServerConsoleLogger myLogger;

  public Maven40TransferListenerAdapter(MavenServerProgressIndicator progressIndicator,
                                        MavenServerConsoleIndicator consoleIndicator,
                                        Maven40ServerConsoleLogger logger) {
    myProgressIndicator = progressIndicator;
    myConsoleIndicator = consoleIndicator;
    myLogger = logger;
  }

  private void checkCanceled() {
    try {
      if (myProgressIndicator != null && myProgressIndicator.isCanceled()) {
        throw new MavenProcessCanceledRuntimeException();
      }
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  private static String formatResourceName(TransferEvent event) {
    TransferResource resource = event.getResource();
    File file = resource.getFile();
    return (file == null ? resource.getResourceName() : file.getName()) + " [" + resource.getRepositoryUrl() + "]";
  }

  @Override
  public void transferInitiated(TransferEvent event) {
    checkCanceled();
    String eventString = formatResourceName(event);
    if (myLogger != null) myLogger.debug(eventString);
  }

  @Override
  public void transferStarted(TransferEvent event) throws TransferCancelledException {
    transferProgressed(event);
  }

  @Override
  public void transferProgressed(TransferEvent event) {
    checkCanceled();

    TransferResource r = event.getResource();
    long totalLength = r.getContentLength();

    String sizeInfo;
    if (totalLength <= 0) {
      sizeInfo = MavenStringUtil.formatFileSize(event.getTransferredBytes()) + " / ?";
    }
    else {
      sizeInfo = MavenStringUtil.formatFileSize(event.getTransferredBytes()) + " / " + MavenStringUtil.formatFileSize(totalLength);
    }

    if (myLogger != null) {
      myLogger.debug(formatResourceName(event) + "  (" + sizeInfo + ')');
      if (totalLength > 0) {
        myLogger.debug(String.valueOf(Math.floor(100 * (double)event.getTransferredBytes() / totalLength)) + "%");
      }
    }
  }

  @Override
  public void transferCorrupted(TransferEvent event) throws TransferCancelledException {
    if (myLogger != null) myLogger.warn("Checksum failed: " + formatResourceName(event));
  }

  @Override
  public void transferSucceeded(TransferEvent event) {
    if (myLogger != null) {
      myLogger.debug("Finished (" + MavenStringUtil.formatFileSize(event.getTransferredBytes()) + ") " + formatResourceName(event));
    }
  }

  @Override
  public void transferFailed(TransferEvent event) {
    try {
      if (myProgressIndicator != null && myProgressIndicator.isCanceled()) {
        if (myLogger != null) myLogger.info("Canceling...");
        return;
      }
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }

    if (myLogger != null) myLogger.warn("Failed to download " + formatResourceName(event));
  }
}
