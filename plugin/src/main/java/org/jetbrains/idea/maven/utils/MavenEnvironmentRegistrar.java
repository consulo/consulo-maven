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

import consulo.annotation.component.ExtensionImpl;
import consulo.application.macro.PathMacroContributor;

import java.util.Map;

@ExtensionImpl
public class MavenEnvironmentRegistrar implements PathMacroContributor {
    private static final String MAVEN_REPOSITORY = "MAVEN_REPOSITORY";

    @Override
    public void registerPathMacros(Map<String, String> map, Map<String, String> legacyNotUsed) {
        map.put(MAVEN_REPOSITORY, MavenUtil.resolveLocalRepository(null, null, null).getPath());
    }
}
