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
package consulo.maven.rt.m3.common.server;


import consulo.maven.rt.server.common.server.MavenServerDownloadListener;
import consulo.maven.rt.server.common.server.MavenServerLogger;

public class Maven3ServerGlobals {
  private static MavenServerLoggerWrapper myLogger;
  private static MavenServerDownloadListener myDownloadListener;

  public static MavenServerLoggerWrapper getLogger() {
    return myLogger;
  }

  public static MavenServerDownloadListener getDownloadListener() {
    return myDownloadListener;
  }


  public static void set(MavenServerLogger logger, MavenServerDownloadListener downloadListener) {
    myLogger = new MavenServerLoggerWrapper(logger);
    myDownloadListener = downloadListener;
  }
}
