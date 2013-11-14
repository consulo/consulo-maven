/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.Path;
import org.mustbe.consulo.roots.ContentFolderTypeProvider;
import org.mustbe.consulo.roots.impl.ProductionContentFolderTypeProvider;
import org.mustbe.consulo.roots.impl.ProductionResourceContentFolderTypeProvider;
import org.mustbe.consulo.roots.impl.TestContentFolderTypeProvider;
import org.mustbe.consulo.roots.impl.TestResourceContentFolderTypeProvider;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import lombok.val;

public class MavenFoldersImporter
{
	private final MavenProject myMavenProject;
	private final MavenImportingSettings myImportingSettings;
	private final MavenRootModelAdapter myModel;

	public static void updateProjectFolders(final Project project, final boolean updateTargetFoldersOnly)
	{
		final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
		final MavenImportingSettings settings = manager.getImportingSettings();

		AccessToken accessToken = WriteAction.start();
		try
		{
			List<ModifiableRootModel> rootModels = new ArrayList<ModifiableRootModel>();
			for(Module each : ModuleManager.getInstance(project).getModules())
			{
				MavenProject mavenProject = manager.findProject(each);
				if(mavenProject == null)
				{
					continue;
				}

				MavenRootModelAdapter a = new MavenRootModelAdapter(mavenProject, each, new MavenDefaultModifiableModelsProvider(project));
				new MavenFoldersImporter(mavenProject, settings, a).config(updateTargetFoldersOnly);

				ModifiableRootModel model = a.getRootModel();
				if(model.isChanged())
				{
					rootModels.add(model);
				}
				else
				{
					model.dispose();
				}
			}

			if(!rootModels.isEmpty())
			{
				ModifiableRootModel[] modelsArray = rootModels.toArray(new ModifiableRootModel[rootModels.size()]);
				if(modelsArray.length > 0)
				{
					ModifiableModelCommitter.multiCommit(modelsArray, ModuleManager.getInstance(modelsArray[0].getProject()).getModifiableModel());
				}
			}
		}
		finally
		{
			accessToken.finish();
		}
	}

	public MavenFoldersImporter(MavenProject mavenProject, MavenImportingSettings settings, MavenRootModelAdapter model)
	{
		myMavenProject = mavenProject;
		myImportingSettings = settings;
		myModel = model;
	}

	public void config()
	{
		config(false);
	}

	private void config(boolean updateTargetFoldersOnly)
	{
		if(!updateTargetFoldersOnly)
		{
			if(!myImportingSettings.isKeepSourceFolders())
			{
				myModel.clearSourceFolders();
			}
			configSourceFolders();
			configOutputFolders();
		}
		configGeneratedAndExcludedFolders();
	}

	private void configSourceFolders()
	{
		MultiMap<ContentFolderTypeProvider, String> temp = new MultiMap<ContentFolderTypeProvider, String>();

		temp.putValues(ProductionContentFolderTypeProvider.getInstance(), myMavenProject.getSources());
		temp.putValues(TestContentFolderTypeProvider.getInstance(), myMavenProject.getTestSources());

		for(MavenImporter each : MavenImporter.getSuitableImporters(myMavenProject))
		{
			each.collectContentFolders(myMavenProject, temp);
		}

		for(MavenResource each : myMavenProject.getResources())
		{
			temp.putValue(ProductionResourceContentFolderTypeProvider.getInstance(), each.getDirectory());
		}

		for(MavenResource each : myMavenProject.getTestResources())
		{
			temp.putValue(TestResourceContentFolderTypeProvider.getInstance(), each.getDirectory());
		}

		addBuilderHelperPaths("add-source", ProductionContentFolderTypeProvider.getInstance(), temp);
		addBuilderHelperPaths("add-test-source", TestContentFolderTypeProvider.getInstance(), temp);

		MultiMap<ContentFolderTypeProvider, Path> allFolders = new MultiMap<ContentFolderTypeProvider, Path>();

		for(Map.Entry<ContentFolderTypeProvider, Collection<String>> entry : temp.entrySet())
		{
			for(String path : entry.getValue())
			{
				allFolders.putValue(entry.getKey(), myModel.toPath(path));
			}
		}

		for(Pair<Path, ContentFolderTypeProvider> each : normalize(allFolders))
		{
			myModel.addSourceFolder(each.first.getPath(), each.second);
		}
	}

	private void addBuilderHelperPaths(String goal, ContentFolderTypeProvider typeProvider, MultiMap<ContentFolderTypeProvider, String> folders)
	{
		val configurationElement = myMavenProject.getPluginGoalConfiguration("org.codehaus.mojo", "build-helper-maven-plugin", goal);
		if(configurationElement != null)
		{
			val sourcesElement = configurationElement.getChild("sources");
			if(sourcesElement != null)
			{
				for(val element : sourcesElement.getChildren())
				{
					folders.putValue(typeProvider, element.getTextTrim());
				}
			}
		}
	}

	@NotNull
	private static List<Pair<Path, ContentFolderTypeProvider>> normalize(@NotNull MultiMap<ContentFolderTypeProvider, Path> folders)
	{
		List<Pair<Path, ContentFolderTypeProvider>> result = new ArrayList<Pair<Path, ContentFolderTypeProvider>>(folders.size());
		for(Map.Entry<ContentFolderTypeProvider, Collection<Path>> entry : folders.entrySet())
		{
			for(Path path : entry.getValue())
			{
				addSourceFolder(path, entry.getKey(), result);
			}
		}
		return result;
	}

	private static void addSourceFolder(Path path, ContentFolderTypeProvider provider, List<Pair<Path, ContentFolderTypeProvider>> result)
	{
		for(Pair<Path, ContentFolderTypeProvider> eachExisting : result)
		{
			if(MavenRootModelAdapter.isEqualOrAncestor(eachExisting.first.getPath(), path.getPath()) || MavenRootModelAdapter.isEqualOrAncestor(path.getPath(), eachExisting.first.getPath()))
			{
				return;
			}
		}
		result.add(new Pair<Path, ContentFolderTypeProvider>(path, provider));
	}

	private void configOutputFolders()
	{
		if(myImportingSettings.isUseMavenOutput())
		{
			myModel.useModuleOutput(myMavenProject.getOutputDirectory(), myMavenProject.getTestOutputDirectory());
		}
		myModel.addExcludedFolder(myMavenProject.getOutputDirectory());
		myModel.addExcludedFolder(myMavenProject.getTestOutputDirectory());
	}

	private void configGeneratedAndExcludedFolders()
	{
		File targetDir = new File(myMavenProject.getBuildDirectory());

		String generatedDir = myMavenProject.getGeneratedSourcesDirectory(false);
		String generatedDirTest = myMavenProject.getGeneratedSourcesDirectory(true);

		myModel.unregisterAll(targetDir.getPath(), true, false);

		if(myImportingSettings.getGeneratedSourcesFolder() != MavenImportingSettings.GeneratedSourcesFolder.IGNORE)
		{
			myModel.addSourceFolder(myMavenProject.getAnnotationProcessorDirectory(true), TestContentFolderTypeProvider.getInstance(), true);
			myModel.addSourceFolder(myMavenProject.getAnnotationProcessorDirectory(false), ProductionContentFolderTypeProvider.getInstance(), true);
		}

		File[] targetChildren = targetDir.listFiles();

		if(targetChildren != null)
		{
			for(File f : targetChildren)
			{
				if(!f.isDirectory())
				{
					continue;
				}

				if(FileUtil.pathsEqual(generatedDir, f.getPath()))
				{
					configGeneratedSourceFolder(f, ProductionContentFolderTypeProvider.getInstance());
				}
				else if(FileUtil.pathsEqual(generatedDirTest, f.getPath()))
				{
					configGeneratedSourceFolder(f, TestContentFolderTypeProvider.getInstance());
				}
				else
				{
					if(myImportingSettings.isExcludeTargetFolder())
					{
						if(myModel.hasRegisteredSourceSubfolder(f))
						{
							continue;
						}
						if(myModel.isAlreadyExcluded(f))
						{
							continue;
						}
						myModel.addExcludedFolder(f.getPath());
					}
				}
			}
		}

		List<String> facetExcludes = new ArrayList<String>();
		for(MavenImporter each : MavenImporter.getSuitableImporters(myMavenProject))
		{
			each.collectExcludedFolders(myMavenProject, facetExcludes);
		}
		for(String eachFolder : facetExcludes)
		{
			myModel.unregisterAll(eachFolder, true, true);
			myModel.addExcludedFolder(eachFolder);
		}

		if(myImportingSettings.isExcludeTargetFolder())
		{
			if(targetChildren == null || !myModel.hasRegisteredSourceSubfolder(targetDir))
			{
				myModel.addExcludedFolder(targetDir.getPath());
			}
		}
		else
		{
			myModel.addExcludedFolder(myMavenProject.getOutputDirectory());
			myModel.addExcludedFolder(myMavenProject.getTestOutputDirectory());
		}
	}

	private void configGeneratedSourceFolder(@NotNull File targetDir, ContentFolderTypeProvider typeProvider)
	{
		switch(myImportingSettings.getGeneratedSourcesFolder())
		{
			case GENERATED_SOURCE_FOLDER:
				myModel.addSourceFolder(targetDir.getPath(), typeProvider, true);
				break;

			case SUBFOLDER:
				addAllSubDirsAsSources(targetDir, typeProvider);
				break;

			case AUTODETECT:
				Collection<JavaModuleSourceRoot> sourceRoots = JavaSourceRootDetectionUtil.suggestRoots(targetDir);

				for(JavaModuleSourceRoot root : sourceRoots)
				{
					if(targetDir.equals(root.getDirectory()))
					{
						myModel.addSourceFolder(targetDir.getPath(), typeProvider);
						return;
					}

					addAsSourceFolder(root.getDirectory(), typeProvider);
				}

				addAllSubDirsAsSources(targetDir, typeProvider);
				break;

			case IGNORE:
				break; // Ignore.
		}
	}

	private void addAsSourceFolder(@NotNull File dir, ContentFolderTypeProvider typeProvider)
	{
		if(!myModel.hasRegisteredSourceSubfolder(dir))
		{
			myModel.addSourceFolder(dir.getPath(), typeProvider, true);
		}
	}

	private void addAllSubDirsAsSources(@NotNull File dir, ContentFolderTypeProvider typeProvider)
	{
		for(File f : getChildren(dir))
		{
			if(f.isDirectory())
			{
				addAsSourceFolder(f, typeProvider);
			}
		}
	}

	private static File[] getChildren(File dir)
	{
		File[] result = dir.listFiles();
		return result == null ? ArrayUtil.EMPTY_FILE_ARRAY : result;
	}
}
