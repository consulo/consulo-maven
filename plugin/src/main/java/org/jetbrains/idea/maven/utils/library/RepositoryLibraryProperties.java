/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library;

import consulo.content.library.LibraryProperties;
import consulo.util.lang.Comparing;
import consulo.util.xml.serializer.annotation.Attribute;

/**
 * @author nik
 */
public class RepositoryLibraryProperties extends LibraryProperties<RepositoryLibraryProperties>
{
  private String myMavenId;

  public RepositoryLibraryProperties() {
  }

  public RepositoryLibraryProperties(String mavenId) {
    myMavenId = mavenId;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof RepositoryLibraryProperties && Comparing.equal(myMavenId, ((RepositoryLibraryProperties)obj).myMavenId);
  }

  @Override
  public int hashCode() {
    return Comparing.hashcode(myMavenId);
  }

  @Override
  public RepositoryLibraryProperties getState() {
    return this;
  }

  @Override
  public void loadState(RepositoryLibraryProperties state) {
    myMavenId = state.myMavenId;
  }

  @Attribute("maven-id")
  public String getMavenId() {
    return myMavenId;
  }

  public void setMavenId(String mavenId) {
    myMavenId = mavenId;
  }
}
