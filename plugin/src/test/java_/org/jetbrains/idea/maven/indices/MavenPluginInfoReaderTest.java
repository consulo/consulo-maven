/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.indices;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.MavenTestCase;
import consulo.maven.rt.server.common.model.MavenId;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenPluginInfo;

public abstract class MavenPluginInfoReaderTest extends MavenTestCase {
  private MavenCustomRepositoryHelper myRepositoryHelper;
  private MavenPluginInfo p;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRepositoryHelper = new MavenCustomRepositoryHelper(myDir, "plugins");

    setRepositoryPath(myRepositoryHelper.getTestDataPath("plugins"));

    MavenId id = new MavenId("org.apache.maven.plugins", "maven-compiler-plugin", "2.0.2");
    p = MavenArtifactUtil.readPluginInfo(getRepositoryFile(), id);
  }

  public void testLoadingPluginInfo() throws Exception {
    assertEquals("org.apache.maven.plugins", p.getGroupId());
    assertEquals("maven-compiler-plugin", p.getArtifactId());
    assertEquals("2.0.2", p.getVersion());
  }

  public void testGoals() throws Exception {
    assertEquals("compiler", p.getGoalPrefix());

    List<String> qualifiedGoals = new ArrayList<String>();
    List<String> displayNames = new ArrayList<String>();
    List<String> goals = new ArrayList<String>();
    for (MavenPluginInfo.Mojo m : p.getMojos()) {
      goals.add(m.getGoal());
      qualifiedGoals.add(m.getQualifiedGoal());
      displayNames.add(m.getDisplayName());
    }

    assertOrderedElementsAreEqual(goals, "compile", "testCompile");
    assertOrderedElementsAreEqual(qualifiedGoals,
                                  "org.apache.maven.plugins:maven-compiler-plugin:2.0.2:compile",
                                  "org.apache.maven.plugins:maven-compiler-plugin:2.0.2:testCompile");
    assertOrderedElementsAreEqual(displayNames, "compiler:compile", "compiler:testCompile");
  }
}
