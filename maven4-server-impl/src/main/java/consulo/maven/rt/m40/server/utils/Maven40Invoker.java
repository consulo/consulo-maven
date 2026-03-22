// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.maven.rt.m40.server.utils;

import consulo.maven.rt.m40.server.InvokerWithoutCoreExtensions;
import consulo.maven.rt.m40.server.compat.CompatResidentMavenInvoker;
import org.apache.maven.api.cli.InvokerRequest;
import org.apache.maven.cling.invoker.LookupContext;
import org.apache.maven.cling.invoker.ProtoLookup;
import org.apache.maven.cling.invoker.mvn.MavenContext;
import org.apache.maven.cling.invoker.mvn.MavenInvoker;
import org.apache.maven.execution.MavenExecutionRequest;

public class Maven40Invoker extends CompatResidentMavenInvoker {
  MavenContext myContext = null;

  private final Maven40ServerConsoleLogger myLogger;

  public Maven40Invoker(ProtoLookup protoLookup, Maven40ServerConsoleLogger logger) {
    super(protoLookup);
    myLogger = logger;
  }

  @Override
  protected int doInvoke(MavenContext context) throws Exception {
    validate(context);
    pushCoreProperties(context);
    pushUserProperties(context);
    configureLogging(context);
    createTerminal(context);
    activateLogging(context);
    helpOrVersionAndMayExit(context);
    preCommands(context);
    //noinspection CastToIncompatibleInterface
    tryRunAndRetryOnFailure(
      "container",
      () -> container(context),
      () -> ((InvokerWithoutCoreExtensions)context.invokerRequest).disableCoreExtensions()
    );
    postContainer(context);
    pushUserProperties(context);
    lookup(context);
    init(context);
    postCommands(context);
    tryRun(
      "settings",
      () -> settings(context),
      () -> context.localRepositoryPath = localRepositoryPath(context)
    );
    //return execute(context);
    myContext = context;
    return 0;
  }

  /**
   * adapted from {@link MavenInvoker#execute(MavenContext)}
   */
  public MavenExecutionRequest createMavenExecutionRequest() throws Exception {
    MavenContext context = myContext;
    MavenExecutionRequest request = prepareMavenExecutionRequest();
    toolchains(context, request);
    populateRequest(context, context.lookup, request);
    return request;
  }

  private boolean tryRun(String methodName, ThrowingRunnable action, Runnable onFailure) {
    try {
      action.run();
    }
    catch (Exception e) {
      if (myLogger != null) myLogger.warn("Maven40Invoker." + methodName + ": " + e.getMessage(), e);
      if (null != onFailure) {
        onFailure.run();
      }
      return false;
    }
    return true;
  }

  private void tryRunAndRetryOnFailure(String methodName, ThrowingRunnable action, Runnable onFailure) {
    if (!tryRun(methodName, action, onFailure)) {
      tryRun(methodName, action, null);
    }
  }

  public LookupContext invokeAndGetContext(InvokerRequest invokerRequest) {
    invoke(invokerRequest);
    return myContext;
  }

  @FunctionalInterface
  interface ThrowingRunnable {
    void run() throws Exception;
  }
}
