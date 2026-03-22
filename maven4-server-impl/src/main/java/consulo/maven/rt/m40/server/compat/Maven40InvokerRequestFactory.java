// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.maven.rt.m40.server.compat;

import consulo.maven.rt.m40.server.InvokerWithoutCoreExtensions;
import org.apache.maven.api.cli.InvokerRequest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Maven40InvokerRequestFactory {

  private static final Map<String, Method> INVOKER_METHODS = new HashMap<>();

  static {
    for (Method m : InvokerRequest.class.getMethods()) {
      INVOKER_METHODS.put(m.getName(), m);
    }
  }

  public static InvokerRequest createProxy(InvokerRequest invokerRequest) {
    return (InvokerRequest)Proxy.newProxyInstance(
      Maven40InvokerRequestFactory.class.getClassLoader(),
      new Class[]{InvokerRequest.class, InvokerWithoutCoreExtensions.class},
      new InvokerProxyHandler(invokerRequest)
    );
  }

  private static class InvokerProxyHandler implements InvocationHandler {

    private final InvokerRequest myInvokerRequest;
    private boolean coreExtensionsDisabled = false;

    public InvokerProxyHandler(InvokerRequest invoker) {
      myInvokerRequest = invoker;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getName().equals("disableCoreExtensions")) {
        coreExtensionsDisabled = true;
        return null;
      }
      else if (method.getName().equals("coreExtensions")) {
        if (coreExtensionsDisabled) return Optional.empty();
        return myInvokerRequest.coreExtensions();
      }
      else if (method.getName().equals("toString")) {
        return "[Proxy]:" + myInvokerRequest.toString();
      }
      else if (method.getName().equals("hashCode")) {
        return myInvokerRequest.hashCode();
      }
      else {
        Method realMethod = Maven40InvokerRequestFactory.INVOKER_METHODS.get(method.getName());
        if (realMethod == null || (args != null && args.length > 0)) {
          throw new UnsupportedOperationException(method.getName() + " is not supported in this version");
        }
        return realMethod.invoke(myInvokerRequest);
      }
    }
  }
}
