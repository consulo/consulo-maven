// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import consulo.maven.rt.server.common.server.MavenArtifactEvent;
import consulo.maven.rt.server.common.server.MavenServerConsoleEvent;
import consulo.maven.rt.server.common.server.MavenServerConsoleIndicator;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.List;

public final class MavenLogEventHandler implements MavenEventHandler {
    public static final MavenLogEventHandler INSTANCE = new MavenLogEventHandler();

    private MavenLogEventHandler() {
    }

    @Override
    public void handleConsoleEvents(@Nonnull List<MavenServerConsoleEvent> consoleEvents) {
        for (MavenServerConsoleEvent e : consoleEvents) {
            String message = e.getMessage();
            switch (e.getLevel()) {
                case MavenServerConsoleIndicator.LEVEL_DEBUG:
                    MavenLog.LOG.debug(message);
                    break;
                case MavenServerConsoleIndicator.LEVEL_INFO:
                    MavenLog.LOG.info(message);
                    break;
                default:
                    MavenLog.LOG.warn(message);
                    break;
            }
            Throwable throwable = e.getThrowable();
            if (throwable != null) {
                MavenLog.LOG.warn(throwable);
            }
        }
    }

    @Override
    public void handleDownloadEvents(@Nonnull List<MavenArtifactEvent> downloadEvents) {
        for (MavenArtifactEvent e : downloadEvents) {
            String id = e.getDependencyId();
            switch (e.getArtifactEventType()) {
                case DOWNLOAD_STARTED:
                    MavenLog.LOG.debug("Download started: " + id);
                    break;
                case DOWNLOAD_COMPLETED:
                    MavenLog.LOG.debug("Download completed: " + id);
                    break;
                case DOWNLOAD_FAILED:
                    MavenLog.LOG.debug("Download failed: " + id + " \n" + e.getErrorMessage() + " \n" + e.getStackTrace());
                    break;
            }
        }
    }
}
