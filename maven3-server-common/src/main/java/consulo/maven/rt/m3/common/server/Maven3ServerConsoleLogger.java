package consulo.maven.rt.m3.common.server;

import org.codehaus.plexus.logging.Logger;
import consulo.maven.rt.server.common.server.MavenServerConsole;

import java.rmi.RemoteException;

public class Maven3ServerConsoleLogger implements Logger {
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  private MavenServerConsole myWrappee;
  private int myThreshold;

  void doPrint(int level, String message, Throwable throwable) {
    if (level < myThreshold) return;

    if (!message.endsWith(LINE_SEPARATOR)) {
      message += LINE_SEPARATOR;
    }

    if (myWrappee != null) {
      try {
        myWrappee.printMessage(level, message, throwable);
      }
      catch (RemoteException e) {
        //todo throw new RuntimeRemoteException(e); ???
      }
    }
  }

  public void setWrappee(MavenServerConsole wrappee) {
    myWrappee = wrappee;
  }

  public void debug(String string, Throwable throwable) {
    doPrint(MavenServerConsole.LEVEL_DEBUG, string, throwable);
  }

  public void info(String string, Throwable throwable) {
    doPrint(MavenServerConsole.LEVEL_INFO, string, throwable);
  }

  public void warn(String string, Throwable throwable) {
    doPrint(MavenServerConsole.LEVEL_WARN, string, throwable);
  }

  public void error(String string, Throwable throwable) {
    doPrint(MavenServerConsole.LEVEL_ERROR, string, throwable);
  }

  public void fatalError(String string, Throwable throwable) {
    doPrint(MavenServerConsole.LEVEL_FATAL, string, throwable);
  }

  public void debug(String message) {
    debug(message, null);
  }

  public boolean isDebugEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_DEBUG;
  }

  public void info(String message) {
    info(message, null);
  }

  public boolean isInfoEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_INFO;
  }

  public void warn(String message) {
    warn(message, null);
  }

  public boolean isWarnEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_WARN;
  }

  public void error(String message) {
    error(message, null);
  }

  public boolean isErrorEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_ERROR;
  }

  public void fatalError(String message) {
    fatalError(message, null);
  }

  public boolean isFatalErrorEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_FATAL;
  }

  public void setThreshold(int threshold) {
    this.myThreshold = threshold;
  }

  public int getThreshold() {
    return myThreshold;
  }

  public Logger getChildLogger(String s) {
    return null;
  }

  public String getName() {
    return toString();
  }
}
