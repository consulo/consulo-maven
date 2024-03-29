/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.maven.rt.m2.server;


import consulo.maven.rt.server.common.server.MavenServerDownloadListener;
import consulo.maven.rt.server.common.server.MavenServerLogger;
import consulo.maven.rt.m2.server.embedder.Maven2ServerLoggerWrapper;

public class Maven2ServerGlobals {
  private static Maven2ServerLoggerWrapper myLogger;
  private static MavenServerDownloadListener myDownloadListener;

  public static Maven2ServerLoggerWrapper getLogger() {
    return myLogger;
  }

  public static MavenServerDownloadListener getDownloadListener() {
    return myDownloadListener;
  }


  public static void set(MavenServerLogger logger, MavenServerDownloadListener downloadListener) {
    myLogger = new Maven2ServerLoggerWrapper(logger);
    myDownloadListener = downloadListener;
  }
}
