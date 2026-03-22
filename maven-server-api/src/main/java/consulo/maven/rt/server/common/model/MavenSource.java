/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors.
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
package consulo.maven.rt.server.common.model;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MavenSource implements Serializable {
  public static final String MAIN_SCOPE = "main";
  public static final String TEST_SCOPE = "test";
  public static final String JAVA_LANG = "java";
  public static final String RESOURCES_LANG = "resources";

  private final String myDirectoryAbsolutePath;
  private final ArrayList<String> myIncludes;
  private final ArrayList<String> myExcludes;
  private final String myScope;
  private final String myLang;
  private final String myTargetPath;
  private final String myTargetVersion;
  private final boolean myFiltered;
  private final boolean myEnabled;
  private final boolean myIsSourceTag;

  private MavenSource(boolean isSourceTag,
                      String directoryAbsolutePath,
                      List<String> includes,
                      List<String> excludes,
                      String scope,
                      String lang,
                      String targetPath,
                      String targetVersion,
                      boolean filtered,
                      boolean enabled) {
    myIsSourceTag = isSourceTag;
    myDirectoryAbsolutePath = directoryAbsolutePath;
    myIncludes = new ArrayList<>(includes);
    myExcludes = new ArrayList<>(excludes);
    myScope = scope;
    myLang = lang;
    myTargetPath = targetPath;
    myTargetVersion = targetVersion;
    myFiltered = filtered;
    myEnabled = enabled;
  }

  public String getDirectory() {
    return myDirectoryAbsolutePath;
  }

  public List<String> getIncludes() {
    return myIncludes;
  }

  public List<String> getExcludes() {
    return myExcludes;
  }

  public String getScope() {
    return myScope;
  }

  public String getLang() {
    return myLang;
  }

  public String getTargetPath() {
    return myTargetPath;
  }

  public String getTargetVersion() {
    return myTargetVersion;
  }

  public boolean isFiltered() {
    return myFiltered;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  /**
   * @return true if this object was generated from maven 4-rc3+ source tag, false otherwise
   */
  public boolean isFromSourceTag() {
    return myIsSourceTag;
  }

  public MavenSource withNewDirectory(String newDir) {
    return new MavenSource(
      myIsSourceTag,
      newDir,
      myIncludes,
      myExcludes,
      myScope,
      myLang,
      myTargetPath,
      myTargetVersion,
      myFiltered,
      myEnabled
    );
  }

  public static boolean isSource(MavenSource src) {
    return src.isEnabled() && (src.getScope() == null || src.getScope().equals(MAIN_SCOPE)) && JAVA_LANG.equals(src.getLang());
  }

  public static boolean isTestSource(MavenSource src) {
    return src.isEnabled() && src.getScope().equals(TEST_SCOPE) && JAVA_LANG.equals(src.getLang());
  }

  public static boolean isResource(MavenSource src) {
    return src.isEnabled() && (src.getScope() == null || src.getScope().equals(MAIN_SCOPE)) && RESOURCES_LANG.equals(src.getLang());
  }

  public static boolean isTestResource(MavenSource src) {
    return src.isEnabled() && src.getScope().equals(TEST_SCOPE) && RESOURCES_LANG.equals(src.getLang());
  }

  public static MavenSource fromSrc(String dir, boolean forTests) {
    return new MavenSource(false, dir, Collections.<String>emptyList(), Collections.<String>emptyList(),
                           forTests ? TEST_SCOPE : MAIN_SCOPE, JAVA_LANG, null, null, false, true);
  }

  public static MavenSource fromResource(MavenResource resource, boolean forTests) {
    return new MavenSource(false, resource.getDirectory(), resource.getIncludes(), resource.getExcludes(),
                           forTests ? TEST_SCOPE : MAIN_SCOPE,
                           RESOURCES_LANG, resource.getTargetPath(), null, resource.isFiltered(), true);
  }

  public static MavenSource fromSourceTag(Path projectPomFile,
                                          String directory,
                                          List<String> includes,
                                          List<String> excludes,
                                          String scope,
                                          String lang,
                                          String targetPath,
                                          String targetVersion,
                                          boolean filtered,
                                          boolean enabled) {
    if (scope == null) {
      scope = MAIN_SCOPE;
    }
    if (lang == null) {
      lang = JAVA_LANG;
    }
    if (directory == null) {
      directory = "src/" + scope + "/" + lang;
    }
    Path absolute = getAbsolutePath(projectPomFile.getParent(), directory);
    return new MavenSource(true, absolute.toString(), includes, excludes, scope, lang, targetPath, targetVersion, filtered, enabled);
  }

  private static Path getAbsolutePath(Path pomDirectory, String directory) {
    Path p = parseToPath(directory);
    if (!p.isAbsolute()) {
      p = pomDirectory.resolve(p);
    }
    return p.toAbsolutePath().normalize();
  }

  private static Path parseToPath(String directory) {
    String trimmed = directory.trim();
    if (trimmed.toLowerCase(Locale.ROOT).startsWith("file:")) {
      try {
        URI uri = new URI(trimmed);
        return Paths.get(uri);
      }
      catch (URISyntaxException e) {
        throw new IllegalArgumentException("Invalid file URI: " + directory, e);
      }
      catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unsupported URI (expected file:): " + directory, e);
      }
    }
    try {
      return Paths.get(trimmed);
    }
    catch (InvalidPathException e) {
      throw new IllegalArgumentException("Invalid path: " + directory, e);
    }
  }

  @Override
  public final boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof MavenSource)) return false;
    MavenSource source = (MavenSource)o;
    return myFiltered == source.myFiltered &&
           myEnabled == source.myEnabled &&
           Objects.equals(myDirectoryAbsolutePath, source.myDirectoryAbsolutePath) &&
           Objects.equals(myIncludes, source.myIncludes) &&
           Objects.equals(myExcludes, source.myExcludes) &&
           Objects.equals(myScope, source.myScope) &&
           Objects.equals(myLang, source.myLang) &&
           Objects.equals(myTargetPath, source.myTargetPath) &&
           Objects.equals(myTargetVersion, source.myTargetVersion);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(myDirectoryAbsolutePath);
    result = 31 * result + Objects.hashCode(myIncludes);
    result = 31 * result + Objects.hashCode(myExcludes);
    result = 31 * result + Objects.hashCode(myScope);
    result = 31 * result + Objects.hashCode(myLang);
    result = 31 * result + Objects.hashCode(myTargetPath);
    result = 31 * result + Objects.hashCode(myTargetVersion);
    result = 31 * result + Boolean.hashCode(myFiltered);
    result = 31 * result + Boolean.hashCode(myEnabled);
    return result;
  }
}
