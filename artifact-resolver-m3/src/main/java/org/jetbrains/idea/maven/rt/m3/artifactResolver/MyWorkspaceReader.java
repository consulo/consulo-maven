/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.rt.m3.artifactResolver;

import org.codehaus.plexus.component.annotations.Component;
import org.jetbrains.idea.maven.artifactResolver.common.MavenModuleMap;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.repository.WorkspaceRepository;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
@Component(role = WorkspaceReader.class, hint = "ide")
public class MyWorkspaceReader implements WorkspaceReader {

  private final WorkspaceRepository myWorkspaceRepository;

  public MyWorkspaceReader() {
    myWorkspaceRepository = new WorkspaceRepository("ide", getClass());
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof MyWorkspaceReader;
  }

  public WorkspaceRepository getRepository() {
    return myWorkspaceRepository;
  }

  public File findArtifact(Artifact artifact) {
    return MavenModuleMap.getInstance().findArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                                                     artifact.getBaseVersion());
  }

  public List<String> findVersions(Artifact artifact) {
    return Collections.emptyList();
  }
}
