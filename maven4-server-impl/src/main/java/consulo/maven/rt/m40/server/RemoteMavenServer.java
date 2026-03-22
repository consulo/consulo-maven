package consulo.maven.rt.m40.server;

import consulo.util.rmi.RemoteServer;

public class RemoteMavenServer extends RemoteServer {
  public static void main(String[] args) throws Exception {
    start(new Maven40ServerImpl());
  }
}
