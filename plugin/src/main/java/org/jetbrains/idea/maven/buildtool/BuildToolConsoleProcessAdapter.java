// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool;

import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.process.internal.AnsiEscapeDecoder;
import consulo.util.dataholder.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenSpyEventsBuffer;

public class BuildToolConsoleProcessAdapter extends ProcessAdapter {
  private final MavenBuildEventProcessor myEventParser;
  private final AnsiEscapeDecoder myDecoder = new AnsiEscapeDecoder();
  private final MavenSpyEventsBuffer myMavenSpyEventsBuffer;


  /**
   * @param processText to be removed after IDEA-216278
   */
  public BuildToolConsoleProcessAdapter(MavenBuildEventProcessor eventParser) {
    myEventParser = eventParser;
    myMavenSpyEventsBuffer = new MavenSpyEventsBuffer((l, k) -> myDecoder.escapeText(l, k, myEventParser));
  }

  @Override
  public void startNotified(@NotNull ProcessEvent event) {
    myEventParser.start();
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    myMavenSpyEventsBuffer.addText(event.getText(), outputType);
  }

  @Override
  public void processTerminated(@NotNull ProcessEvent event) {
    myEventParser.finish();
  }
}
