// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.maven.rt.m40.server.compat;

import org.apache.maven.api.cli.InvokerException;
import org.apache.maven.api.cli.InvokerRequest;
import org.apache.maven.api.services.Lookup;
import org.apache.maven.cling.invoker.mvn.MavenContext;
import org.apache.maven.cling.invoker.mvn.MavenInvoker;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class CompatResidentMavenInvoker extends MavenInvoker {

  private final ConcurrentHashMap<String, MavenContext> residentContext;

  public CompatResidentMavenInvoker(Lookup protoLookup) {
    super(protoLookup, null);
    this.residentContext = new ConcurrentHashMap<>();
  }

  @Override
  public void close() throws InvokerException {
    ArrayList<Exception> exceptions = new ArrayList<>();
    for (MavenContext context : residentContext.values()) {
      try {
        context.doCloseContainer();
      }
      catch (Exception e) {
        exceptions.add(e);
      }
    }
    if (!exceptions.isEmpty()) {
      InvokerException exception = new InvokerException("Could not cleanly shut down context pool");
      exceptions.forEach(exception::addSuppressed);
      throw exception;
    }
  }

  @Override
  protected MavenContext createContext(InvokerRequest invokerRequest) {
    MavenContext result = residentContext.computeIfAbsent(
      "resident",
      k -> MavenContextFactory.createMavenContext(invokerRequest));
    return copyIfDifferent(result, invokerRequest);
  }

  protected MavenContext copyIfDifferent(MavenContext mavenContext, InvokerRequest invokerRequest) {
    if (invokerRequest == mavenContext.invokerRequest) {
      return mavenContext;
    }
    MavenContext shadow = MavenContextFactory.createMavenContext(invokerRequest);

    shadow.containerCapsule = mavenContext.containerCapsule;
    shadow.lookup = mavenContext.lookup;
    shadow.eventSpyDispatcher = mavenContext.eventSpyDispatcher;
    shadow.maven = mavenContext.maven;

    return shadow;
  }
}
