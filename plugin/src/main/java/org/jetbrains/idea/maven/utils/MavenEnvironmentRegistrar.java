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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.macro.PathMacros;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.File;

@Singleton
@ServiceAPI(value = ComponentScope.APPLICATION, lazy = false)
@ServiceImpl
public class MavenEnvironmentRegistrar
{
	private static final String MAVEN_REPOSITORY = "MAVEN_REPOSITORY";

	@Inject
	public MavenEnvironmentRegistrar(PathMacros macros)
	{
		registerPathVariable(macros);
	}

	private void registerPathVariable(PathMacros macros)
	{
		File repository = MavenUtil.resolveLocalRepository(null, null, null);

		for(String each : macros.getAllMacroNames())
		{
			String path = macros.getValue(each);
			if(path == null)
			{
				continue;
			}
			if(new File(path).equals(repository))
			{
				return;
			}
		}
		macros.setMacro(MAVEN_REPOSITORY, repository.getPath());
	}
}
