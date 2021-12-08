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

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import consulo.java.module.extension.JavaMutableModuleExtensionImpl;
import consulo.maven.importing.MavenImportSession;
import consulo.maven.module.extension.MavenMutableModuleExtension;
import consulo.maven.util.MavenJdkUtil;
import consulo.roots.types.BinariesOrderRootType;
import consulo.roots.types.DocumentationOrderRootType;
import consulo.roots.types.SourcesOrderRootType;
import consulo.vfs.util.ArchiveVfsUtil;
import org.jdom.Element;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MavenModuleImporter
{

	public static final String SUREFIRE_PLUGIN_LIBRARY_NAME = "maven-surefire-plugin urls";

	private static final Map<String, LanguageLevel> MAVEN_IDEA_PLUGIN_LEVELS = ImmutableMap.of(
			"JDK_1_3", LanguageLevel.JDK_1_3,
			"JDK_1_4", LanguageLevel.JDK_1_4,
			"JDK_1_5", LanguageLevel.JDK_1_5,
			"JDK_1_6", LanguageLevel.JDK_1_6,
			"JDK_1_7", LanguageLevel.JDK_1_7);

	private final Module myModule;
	private final MavenProjectsTree myMavenTree;
	private final MavenProject myMavenProject;

	@Nullable
	private final MavenProjectChanges myMavenProjectChanges;
	private final Map<MavenProject, String> myMavenProjectToModuleName;
	private final MavenImportingSettings mySettings;
	private final MavenModifiableModelsProvider myModifiableModelsProvider;
	private MavenRootModelAdapter myRootModelAdapter;

	public MavenModuleImporter(Module module,
							   MavenProjectsTree mavenTree,
							   MavenProject mavenProject,
							   @Nullable MavenProjectChanges changes,
							   Map<MavenProject, String> mavenProjectToModuleName,
							   MavenImportingSettings settings,
							   MavenModifiableModelsProvider modifiableModelsProvider)
	{
		myModule = module;
		myMavenTree = mavenTree;
		myMavenProject = mavenProject;
		myMavenProjectChanges = changes;
		myMavenProjectToModuleName = mavenProjectToModuleName;
		mySettings = settings;
		myModifiableModelsProvider = modifiableModelsProvider;
	}

	public ModifiableRootModel getRootModel()
	{
		return myRootModelAdapter.getRootModel();
	}

	public void config(boolean isNewlyCreatedModule, MavenImportSession session)
	{
		myRootModelAdapter = new MavenRootModelAdapter(myMavenProject, myModule, myModifiableModelsProvider);
		myRootModelAdapter.init(isNewlyCreatedModule);

		final ModifiableRootModel rootModel = myModifiableModelsProvider.getRootModel(myModule);

		JavaMutableModuleExtensionImpl javaModuleExtension = rootModel.getExtensionWithoutCheck(JavaMutableModuleExtensionImpl.class);
		javaModuleExtension.setEnabled(true);

		String bytecodeVersion = myMavenProject.getTargetLevel();
		if(bytecodeVersion != null)
		{
			javaModuleExtension.setBytecodeVersion(bytecodeVersion);
		}

		LanguageLevel languageLevel = configureLanguageLevel();

		configureJavaSdk(languageLevel, javaModuleExtension, session);

		ModuleExtensionWithSdkOrderEntry moduleExtensionSdkEntry = rootModel.findModuleExtensionSdkEntry(javaModuleExtension);
		if(moduleExtensionSdkEntry == null)
		{
			rootModel.addModuleExtensionSdkEntry(javaModuleExtension);
		}
		rootModel.getExtensionWithoutCheck(MavenMutableModuleExtension.class).setEnabled(true);

		configFolders();
		configDependencies();
	}

	private LanguageLevel configureLanguageLevel()
	{
		if("false".equalsIgnoreCase(System.getProperty("idea.maven.configure.language.level")))
		{
			return LanguageLevel.HIGHEST;
		}

		LanguageLevel level = null;

		Element cfg = myMavenProject.getPluginConfiguration("com.googlecode", "maven-idea-plugin");
		if(cfg != null)
		{
			level = MAVEN_IDEA_PLUGIN_LEVELS.get(cfg.getChildTextTrim("jdkLevel"));
		}

		if(level == null)
		{
			String mavenProjectSourceLevel = myMavenProject.getSourceLevel();
			level = LanguageLevel.parse(mavenProjectSourceLevel);
			if(level == null)
			{
				String mavenProjectReleaseLevel = myMavenProject.getReleaseLevel();
				level = LanguageLevel.parse(mavenProjectReleaseLevel);
				if(level == null && (StringUtil.isNotEmpty(mavenProjectSourceLevel) || StringUtil.isNotEmpty(mavenProjectReleaseLevel)))
				{
					level = LanguageLevel.HIGHEST;
				}
			}
		}

		// default source and target settings of maven-compiler-plugin is 1.6, see details at http://maven.apache.org/plugins/maven-compiler-plugin
		if(level == null)
		{
			level = LanguageLevel.JDK_1_6;
		}

		myRootModelAdapter.setLanguageLevel(level);
		String compilerId = myMavenProject.getCompilerId();
		if("javi".equals(compilerId))
		{
			// javi compiler allow compilation from jdk 8+
			return LanguageLevel.JDK_1_8;
		}

		return level;
	}

	private void configureJavaSdk(LanguageLevel level, JavaMutableModuleExtensionImpl javaMutableModuleExtension, MavenImportSession session)
	{
		Sdk targetSdk = session.getOrCalculate(level, languageLevel -> {
			MavenRunner mavenRunner = MavenRunner.getInstance(javaMutableModuleExtension.getProject());
			return MavenJdkUtil.findSdkOfLevel(languageLevel, mavenRunner.getState().getJreName());
		});

		javaMutableModuleExtension.getInheritableSdk().set(null, targetSdk);
	}

	public void preConfigFacets()
	{
		MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), new Runnable()
		{
			@Override
			public void run()
			{
				if(myModule.isDisposed())
				{
					return;
				}

				for(final MavenImporter importer : getSuitableImporters())
				{
					final MavenProjectChanges changes;
					if(myMavenProjectChanges == null)
					{
						if(importer.processChangedModulesOnly())
						{
							continue;
						}
						changes = MavenProjectChanges.NONE;
					}
					else
					{
						changes = myMavenProjectChanges;
					}

					importer.preProcess(myModule, myMavenProject, changes, myModifiableModelsProvider);
				}
			}
		});
	}

	public void configFacets(final List<MavenProjectsProcessorTask> postTasks)
	{
		MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), new Runnable()
		{
			@Override
			public void run()
			{
				if(myModule.isDisposed())
				{
					return;
				}

				for(final MavenImporter importer : getSuitableImporters())
				{
					final MavenProjectChanges changes;
					if(myMavenProjectChanges == null)
					{
						if(importer.processChangedModulesOnly())
						{
							continue;
						}
						changes = MavenProjectChanges.NONE;
					}
					else
					{
						changes = myMavenProjectChanges;
					}

					importer.process(myModifiableModelsProvider, myModule, myRootModelAdapter, myMavenTree, myMavenProject, changes, myMavenProjectToModuleName, postTasks);
				}
			}
		});
	}

	private List<MavenImporter> getSuitableImporters()
	{
		return myMavenProject.getSuitableImporters();
	}

	private void configFolders()
	{
		new MavenFoldersImporter(myMavenProject, mySettings, myRootModelAdapter).config();
	}

	private void configDependencies()
	{
		Set<String> dependencyTypesFromSettings = ReadAction.compute(() ->
		{
			if(myModule.getProject().isDisposed())
			{
				return null;
			}

			return MavenProjectsManager.getInstance(myModule.getProject()).getImportingSettings().getDependencyTypesAsSet();
		});

		for(MavenArtifact artifact : myMavenProject.getDependencies())
		{
			String dependencyType = artifact.getType();

			if(!dependencyTypesFromSettings.contains(dependencyType) && !myMavenProject.getDependencyTypesFromImporters(SupportedRequestType.FOR_IMPORT).contains(dependencyType))
			{
				continue;
			}

			DependencyScope scope = selectScope(artifact.getScope());

			MavenProject depProject = myMavenTree.findProject(artifact.getMavenId());

			if(depProject != null && !myMavenTree.isIgnored(depProject))
			{
				if(depProject == myMavenProject)
				{
					continue;
				}
				boolean isTestJar = MavenConstants.TYPE_TEST_JAR.equals(artifact.getType()) || "tests".equals(artifact.getClassifier());
				myRootModelAdapter.addModuleDependency(myMavenProjectToModuleName.get(depProject), scope, isTestJar);

				Element buildHelperCfg = depProject.getPluginGoalConfiguration("org.codehaus.mojo", "build-helper-maven-plugin", "attach-artifact");
				if(buildHelperCfg != null)
				{
					addAttachArtifactDependency(buildHelperCfg, scope, depProject, artifact);
				}

				if(artifact.getClassifier() != null && !"system".equals(artifact.getScope()) && !"false".equals(System.getProperty("idea.maven" +
						"" +
						".classifier.dep")))
				{
					MavenArtifact a = new MavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getBaseVersion(), artifact.getType(),
							artifact.getClassifier(), artifact.getScope(), artifact.isOptional(), artifact.getExtension(), null, myMavenProject.getLocalRepository(), false, false);

					myRootModelAdapter.addLibraryDependency(a, scope, myModifiableModelsProvider, myMavenProject);
				}
			}
			else if("system".equals(artifact.getScope()))
			{
				myRootModelAdapter.addSystemDependency(artifact, scope);
			}
			else
			{
				myRootModelAdapter.addLibraryDependency(artifact, scope, myModifiableModelsProvider, myMavenProject);
			}
		}

		configSurefirePlugin();
	}

	private void configSurefirePlugin()
	{
		// Remove "maven-surefire-plugin urls" library created by previous version of IDEA.
		// todo remove this code after 01.06.2013
		LibraryTable moduleLibraryTable = myRootModelAdapter.getRootModel().getModuleLibraryTable();

		Library library = moduleLibraryTable.getLibraryByName(SUREFIRE_PLUGIN_LIBRARY_NAME);
		if(library != null)
		{
			moduleLibraryTable.removeLibrary(library);
		}
	}

	private void addAttachArtifactDependency(@Nonnull Element buildHelperCfg, @Nonnull DependencyScope scope, @Nonnull MavenProject mavenProject, @Nonnull MavenArtifact artifact)
	{
		Library.ModifiableModel libraryModel = null;

		for(Element artifactsElement : buildHelperCfg.getChildren("artifacts"))
		{
			for(Element artifactElement : artifactsElement.getChildren("artifact"))
			{
				String typeString = artifactElement.getChildTextTrim("type");
				if(typeString != null && !typeString.equals("jar"))
				{
					continue;
				}

				OrderRootType rootType = BinariesOrderRootType.getInstance();

				String classifier = artifactElement.getChildTextTrim("classifier");
				if("sources".equals(classifier))
				{
					rootType = SourcesOrderRootType.getInstance();
				}
				else if("javadoc".equals(classifier))
				{
					rootType = DocumentationOrderRootType.getInstance();
				}

				String filePath = artifactElement.getChildTextTrim("file");
				if(StringUtil.isEmpty(filePath))
				{
					continue;
				}

				VirtualFile file = VfsUtil.findRelativeFile(filePath, mavenProject.getDirectoryFile());
				if(file == null)
				{
					continue;
				}

				file = ArchiveVfsUtil.getArchiveRootForLocalFile(file);
				if(file == null)
				{
					continue;
				}

				if(libraryModel == null)
				{
					String libraryName = artifact.getLibraryName();
					assert libraryName.startsWith(MavenArtifact.MAVEN_LIB_PREFIX);
					libraryName = MavenArtifact.MAVEN_LIB_PREFIX + "ATTACHED-JAR: " + libraryName.substring(MavenArtifact.MAVEN_LIB_PREFIX.length());

					Library library = myModifiableModelsProvider.getLibraryByName(libraryName);
					if(library == null)
					{
						library = myModifiableModelsProvider.createLibrary(libraryName);
					}
					libraryModel = myModifiableModelsProvider.getLibraryModel(library);

					LibraryOrderEntry entry = myRootModelAdapter.getRootModel().addLibraryEntry(library);
					entry.setScope(scope);
				}

				libraryModel.addRoot(file, rootType);
			}
		}
	}

	@Nonnull
	public static DependencyScope selectScope(String mavenScope)
	{
		if(MavenConstants.SCOPE_RUNTIME.equals(mavenScope))
		{
			return DependencyScope.RUNTIME;
		}
		if(MavenConstants.SCOPE_TEST.equals(mavenScope))
		{
			return DependencyScope.TEST;
		}
		if(MavenConstants.SCOPE_PROVIDED.equals(mavenScope))
		{
			return DependencyScope.PROVIDED;
		}
		return DependencyScope.COMPILE;
	}
}
