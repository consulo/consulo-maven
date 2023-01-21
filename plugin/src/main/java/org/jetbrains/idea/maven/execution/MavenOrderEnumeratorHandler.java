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
 *//*

package org.jetbrains.idea.maven.execution;

import consulo.module.Module;
import consulo.project.Project;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.annotation.Nonnull;

public class MavenOrderEnumeratorHandler extends consulo.module.impl.internal.layer.OrderEnumerationHandler
{
	public static class FactoryImpl extends consulo.module.impl.internal.layer.OrderEnumerationHandler.Factory
	{
		@Override
		public boolean isApplicable(@Nonnull Project project)
		{
			return MavenProjectsManager.getInstance(project).isMavenizedProject();
		}

		@Override
		public boolean isApplicable(@Nonnull Module module)
		{
			final MavenProjectsManager manager = MavenProjectsManager.getInstance(module.getProject());
			return manager.isMavenizedModule(module);
		}

		@Override
		public consulo.module.impl.internal.layer.OrderEnumerationHandler createHandler(@javax.annotation.Nullable consulo.module.Module module)
		{
			return INSTANCE;
		}
	}

	private static final MavenOrderEnumeratorHandler INSTANCE = new MavenOrderEnumeratorHandler();

	@Override
	public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath()
	{
		return true;
	}

	@Override
	public boolean shouldIncludeTestsFromDependentModulesToTestClasspath()
	{
		return false;
	}

	@Override
	public boolean shouldProcessDependenciesRecursively()
	{
		return false;
	}
}
*/
