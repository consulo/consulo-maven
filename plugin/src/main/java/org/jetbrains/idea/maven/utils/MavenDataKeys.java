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
package org.jetbrains.idea.maven.utils;

import consulo.execution.RunnerAndConfigurationSettings;
import consulo.util.dataholder.Key;
import consulo.maven.rt.server.common.model.MavenArtifact;
import consulo.maven.rt.server.common.model.MavenProfileKind;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;


public interface MavenDataKeys
{
	Key<List<String>> MAVEN_GOALS = Key.create("MAVEN_GOALS");
	Key<RunnerAndConfigurationSettings> RUN_CONFIGURATION = Key.create("MAVEN_RUN_CONFIGURATION");
	Key<Map<String, MavenProfileKind>> MAVEN_PROFILES = Key.create("MAVEN_PROFILES");
	Key<Collection<MavenArtifact>> MAVEN_DEPENDENCIES = Key.create("MAVEN_DEPENDENCIES");
	Key<JTree> MAVEN_PROJECTS_TREE = Key.create("MAVEN_PROJECTS_TREE");
}
