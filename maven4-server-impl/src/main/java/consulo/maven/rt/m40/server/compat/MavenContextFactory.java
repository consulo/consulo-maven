// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.maven.rt.m40.server.compat;

import consulo.maven.rt.server.common.server.MavenServerEmbedder;
import org.apache.maven.api.cli.InvokerRequest;
import org.apache.maven.api.cli.mvn.MavenOptions;
import org.apache.maven.cling.invoker.mvn.MavenContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public class MavenContextFactory {
  public static MavenContext createMavenContext(InvokerRequest invokerRequest) {
    String mavenVersion = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_VERSION);
    if ("4.0.0-rc-3".equals(mavenVersion)) {
      Constructor<?>[] constructors = MavenContext.class.getConstructors();
      Constructor<?> constructor = Arrays.stream(constructors).filter(it -> it.getParameterCount() == 2).findFirst().orElse(null);
      if (constructors.length != 2 || constructor == null) {
        throw new UnsupportedOperationException("MavenContext: Wrong constructors. This maven is incompatible with current version");
      }
      try {
        return (MavenContext)constructor.newInstance(invokerRequest, false);
      }
      catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
        throw new UnsupportedOperationException("This maven is incompatible with current version", e);
      }
    }
    else {
      MavenOptions mavenOptions = (MavenOptions)invokerRequest.options().orElse(null);
      return new MavenContext(invokerRequest, false, mavenOptions);
    }
  }
}
