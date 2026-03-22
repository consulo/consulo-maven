// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.maven.rt.m40.server.utils;

import consulo.maven.rt.server.common.server.MavenServerConsole;
import consulo.maven.rt.server.common.server.MavenRemoteObject;
import org.apache.maven.api.cli.Logger;

import java.rmi.RemoteException;

public class Maven40ServerConsoleLogger extends MavenRemoteObject implements Logger {
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  private MavenServerConsole myWrappee;
  private int myThreshold;

  public void setWrappee(MavenServerConsole wrappee) {
    myWrappee = wrappee;
  }

  void doPrint(int level, String message, Throwable throwable) {
    if (level < myThreshold) return;

    if (!message.endsWith(LINE_SEPARATOR)) {
      message += LINE_SEPARATOR;
    }

    if (myWrappee != null) {
      try {
        myWrappee.printMessage(level, message, wrapException(throwable));
      }
      catch (RemoteException e) {
        // ignore
      }
    }
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

  @Override
  public void log(Level level, String message, Throwable error) {
    switch (level) {
      case DEBUG:
        debug(message, error);
        break;
      case INFO:
        info(message, error);
        break;
      case WARN:
        warn(message, error);
        break;
      case ERROR:
        error(message, error);
        break;
    }
  }

  @Override
  public void debug(String message) {
    debug(message, null);
  }

  public boolean isDebugEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_DEBUG;
  }

  @Override
  public void info(String message) {
    info(message, null);
  }

  public boolean isInfoEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_INFO;
  }

  @Override
  public void warn(String message) {
    warn(message, null);
  }

  public boolean isWarnEnabled() {
    return getThreshold() <= MavenServerConsole.LEVEL_WARN;
  }

  @Override
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

  public String getName() {
    return toString();
  }
}
