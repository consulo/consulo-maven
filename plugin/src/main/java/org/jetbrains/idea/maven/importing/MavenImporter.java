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
package org.jetbrains.idea.maven.importing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.project.ResolveContext;
import org.jetbrains.idea.maven.project.SupportedRequestType;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.roots.ContentFolderTypeProvider;
import consulo.roots.impl.ProductionContentFolderTypeProvider;
import consulo.roots.impl.TestContentFolderTypeProvider;


public abstract class MavenImporter
{
	public static ExtensionPointName<MavenImporter> EXTENSION_POINT_NAME = ExtensionPointName.create("org.jetbrains.idea.maven.importer");


	public static List<MavenImporter> getSuitableImporters(MavenProject p)
	{
		final List<MavenImporter> result = new ArrayList<MavenImporter>();
		for(MavenImporter importer : EXTENSION_POINT_NAME.getExtensions())
		{
			if(importer.isApplicable(p))
			{
				result.add(importer);
			}
		}
		return result;
	}

	public abstract boolean isApplicable(MavenProject mavenProject);

	public void getSupportedPackagings(Collection<String> result)
	{
	}

	public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type)
	{
	}

	public void getSupportedDependencyScopes(Collection<String> result)
	{
	}

	@Nullable
	public Pair<String, String> getExtraArtifactClassifierAndExtension(MavenArtifact artifact, MavenExtraArtifactType type)
	{
		return null;
	}

	public void resolve(Project project,
			MavenProject mavenProject,
			NativeMavenProjectHolder nativeMavenProject,
			MavenEmbedderWrapper embedder,
			ResolveContext resolveContext) throws MavenProcessCanceledException
	{
	}

	public abstract void preProcess(Module module, MavenProject mavenProject, MavenProjectChanges changes, MavenModifiableModelsProvider modifiableModelsProvider);

	public abstract void process(MavenModifiableModelsProvider modifiableModelsProvider,
			Module module,
			MavenRootModelAdapter rootModel,
			MavenProjectsTree mavenModel,
			MavenProject mavenProject,
			MavenProjectChanges changes,
			Map<MavenProject, String> mavenProjectToModuleName,
			List<MavenProjectsProcessorTask> postTasks);

	@SuppressWarnings("unchecked")
	public <T extends ModuleExtension<T>> T enableModuleExtension(Module module, MavenModifiableModelsProvider modelsProvider, Class<T> clazz)
	{
		final ModifiableRootModel rootModel = modelsProvider.getRootModel(module);

		final MutableModuleExtension<T> extensionWithoutCheck = (MutableModuleExtension<T>) rootModel.getExtensionWithoutCheck(clazz);

		extensionWithoutCheck.setEnabled(true);

		return (T) extensionWithoutCheck;
	}

	public boolean processChangedModulesOnly()
	{
		return true;
	}

	public void collectContentFolders(MavenProject mavenProject, MultiMap<ContentFolderTypeProvider, String> result)
	{
		List<String> list = new SmartList<String>();

		collectSourceFolders(mavenProject, list);

		for(String s : list)
		{
			result.putValue(ProductionContentFolderTypeProvider.getInstance(), s);
		}

		list.clear();

		collectTestFolders(mavenProject, list);

		for(String s : list)
		{
			result.putValue(TestContentFolderTypeProvider.getInstance(), s);
		}

		list.clear();

		/*collectExcludedFolders(mavenProject, list);

		for(String s : list)
		{
			result.putValue(ExcludedContentFolderTypeProvider.getInstance(), s);
		}

		list.clear();  */
	}

	@Deprecated
	public void collectSourceFolders(MavenProject mavenProject, List<String> result)
	{
	}

	@Deprecated
	public void collectTestFolders(MavenProject mavenProject, List<String> result)
	{
	}

	//@Deprecated
	public void collectExcludedFolders(MavenProject mavenProject, List<String> result)
	{
	}
}
