// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.maven.rt.m40.server.utils;

import consulo.maven.rt.server.common.model.MavenWorkspaceMap;
import consulo.maven.rt.server.common.server.MavenServerConsoleIndicator;
import consulo.maven.rt.server.common.server.MavenServerProgressIndicator;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.resolver.RepositorySystemSessionFactory;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public class Maven40RepositorySystemSessionFactory implements RepositorySystemSessionFactory {
  private final RepositorySystemSessionFactory mySystemSessionFactory;
  private final MavenWorkspaceMap myWorkspaceMap;
  private final MavenServerProgressIndicator myProgressIndicator;
  private final MavenServerConsoleIndicator myConsoleIndicator;
  private final Maven40ServerConsoleLogger myLogger;
  private final Consumer<RepositorySystemSession.SessionBuilder> myModifier;

  public Maven40RepositorySystemSessionFactory(RepositorySystemSessionFactory systemSessionFactory,
                                               MavenWorkspaceMap map,
                                               MavenServerProgressIndicator progressIndicator,
                                               MavenServerConsoleIndicator consoleIndicator,
                                               Maven40ServerConsoleLogger logger,
                                               Consumer<RepositorySystemSession.SessionBuilder> builderModifier) {
    mySystemSessionFactory = systemSessionFactory;
    myWorkspaceMap = map;
    myProgressIndicator = progressIndicator;
    myConsoleIndicator = consoleIndicator;
    myLogger = logger;
    myModifier = builderModifier;
  }

  @Override
  public RepositorySystemSession.SessionBuilder newRepositorySessionBuilder(MavenExecutionRequest request) {
    RepositorySystemSession.SessionBuilder builder = mySystemSessionFactory.newRepositorySessionBuilder(request);
    builder
      .setWorkspaceReader(new Maven40WorkspaceMapReader(myWorkspaceMap))
      .setTransferListener(new Maven40TransferListenerAdapter(myProgressIndicator, myConsoleIndicator, myLogger))
      .setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true)
      .setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true)
      .setConfigProperty(ConfigurationProperties.USER_AGENT, getUserAgent());
    if (myModifier != null) myModifier.accept(builder);
    return builder;
  }

  private String getUserAgent() {
    String mavenUA = tryToGetMavenUserAgent();
    String version = System.getProperty("idea.version");
    StringBuilder result = new StringBuilder();
    if (mavenUA != null) {
      result.append(mavenUA).append(";");
    }
    if (version != null) {
      result.append("Consulo (").append(version).append(")");
    }
    else {
      result.append("Consulo");
    }
    return result.toString();
  }

  private String tryToGetMavenUserAgent() {
    try {
      Method m = mySystemSessionFactory.getClass().getDeclaredMethod("getUserAgent");
      m.setAccessible(true);
      Object invoke = m.invoke(mySystemSessionFactory);
      if (invoke instanceof String) return (String)invoke;
      return null;
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignore) {
      return null;
    }
  }
}
