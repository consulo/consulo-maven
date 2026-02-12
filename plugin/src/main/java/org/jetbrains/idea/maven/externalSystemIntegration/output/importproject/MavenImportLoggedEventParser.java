// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.importproject;

import consulo.build.ui.event.BuildEvent;
import consulo.build.ui.output.BuildOutputInstantReader;
import consulo.component.extension.ExtensionPointName;
import consulo.maven.rt.server.common.model.MavenProjectProblem;
import consulo.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.MavenImportEventProcessor;

import java.util.function.Consumer;

/**
 * Log parser for maven import vm process.
 * {@link MavenImportOutputParser}
 * {@link MavenImportEventProcessor}
 * {@link AbstractMavenServerRemoteProcessSupport}
 */
@ApiStatus.Experimental
public interface MavenImportLoggedEventParser {
  ExtensionPointName<MavenImportLoggedEventParser> EP_NAME = ExtensionPointName.create("org.jetbrains.idea.maven.log.import.parser");

  /**
   * Processing log line from vm process - maven server.
   *
   * @param project         - project
   * @param logLine         - log line
   * @param reader          - log reader
   * @param messageConsumer - message consumer (MavenSyncConsole)
   * @return true if log line consumed by messageConsumer
   */
  boolean processLogLine(
    @NotNull Project project,
    @NotNull String logLine,
    @Nullable BuildOutputInstantReader reader,
    @NotNull Consumer<? super BuildEvent> messageConsumer);

  default boolean processProjectProblem(
    @NotNull Project project,
    @NotNull MavenProjectProblem problem
  ) {
    return false;
  }
}

