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
package org.jetbrains.idea.maven.importing;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.JarArchiveFileType;
import consulo.application.ReadAction;
import consulo.application.util.function.Processor;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.content.ContentFolderTypeProvider;
import consulo.content.OrderRootType;
import consulo.content.base.*;
import consulo.content.library.Library;
import consulo.java.impl.module.extension.JavaMutableModuleExtensionImpl;
import consulo.language.content.*;
import consulo.maven.rt.server.common.model.MavenArtifact;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.module.ModifiableModuleModel;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ContentFolder;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.layer.orderEntry.*;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.Path;
import org.jetbrains.idea.maven.utils.Url;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

public class MavenRootModelAdapter
{
	private final MavenProject myMavenProject;
	private final ModifiableModuleModel myModuleModel;
	private final ModifiableRootModel myRootModel;

	public MavenRootModelAdapter(@Nonnull MavenProject p, @Nonnull Module module, final MavenModifiableModelsProvider rootModelsProvider)
	{
		myMavenProject = p;
		myModuleModel = rootModelsProvider.getModuleModel();
		myRootModel = rootModelsProvider.getRootModel(module);
	}

	public void init(boolean isNewlyCreatedModule)
	{
		setupInitialValues(isNewlyCreatedModule);
		initContentRoots();
		initOrderEntries();
	}

	private void setupInitialValues(boolean newlyCreatedModule)
	{
	}

	private void initContentRoots()
	{
		Url url = toUrl(myMavenProject.getDirectory());
		if(getContentRootFor(url) != null)
		{
			return;
		}
		myRootModel.addContentEntry(url.getUrl());
	}

	private ContentEntry getContentRootFor(Url url)
	{
		for(ContentEntry e : myRootModel.getContentEntries())
		{
			if(isEqualOrAncestor(e.getUrl(), url.getUrl()))
			{
				return e;
			}
		}
		return null;
	}

	private void initOrderEntries()
	{
		for(OrderEntry e : myRootModel.getOrderEntries())
		{
			if(e instanceof ModuleSourceOrderEntry || e instanceof ModuleExtensionWithSdkOrderEntry)
			{
				continue;
			}
			if(e instanceof LibraryOrderEntry)
			{
				if(!isMavenLibrary(((LibraryOrderEntry) e).getLibrary()))
				{
					continue;
				}
			}
			if(e instanceof ModuleOrderEntry)
			{
				consulo.module.Module m = ((ModuleOrderEntry) e).getModule();
				if(m != null && !MavenProjectsManager.getInstance(myRootModel.getProject()).isMavenizedModule(m))
				{
					continue;
				}
			}
			myRootModel.removeOrderEntry(e);
		}
	}

	public ModifiableRootModel getRootModel()
	{
		return myRootModel;
	}

	public Module getModule()
	{
		return myRootModel.getModule();
	}

	public void clearSourceFolders()
	{
		for(ContentEntry each : myRootModel.getContentEntries())
		{
			for(ContentFolder contentFolder : each.getFolders(LanguageContentFolderScopes.all(false)))
			{
				each.removeFolder(contentFolder);
			}
		}
	}

	public void addSourceFolder(String path, ContentFolderTypeProvider contentFolderTypeProvider, boolean generated)
	{
		addSourceFolder(path, contentFolderTypeProvider, false, generated);
	}

	public void addSourceFolder(String path, ContentFolderTypeProvider contentFolderTypeProvider, boolean ifNotEmpty, boolean generated)
	{
		if(ifNotEmpty)
		{
			String[] childs = new File(toPath(path).getPath()).list();
			if(childs == null || childs.length == 0)
			{
				return;
			}
		}
		else
		{
			if(!exists(path))
			{
				return;
			}
		}

		Url url = toUrl(path);
		ContentEntry e = getContentRootFor(url);
		if(e == null)
		{
			return;
		}
		unregisterAll(path, true, true);
		unregisterAll(path, false, true);
		ContentFolder contentFolder = e.addFolder(url.getUrl(), contentFolderTypeProvider);
		if(generated)
		{
			contentFolder.setPropertyValue(GeneratedContentFolderPropertyProvider.IS_GENERATED, Boolean.TRUE);
		}
	}

	public boolean hasRegisteredSourceSubfolder(File f)
	{
		String url = toUrl(f.getPath()).getUrl();
		for(ContentEntry eachEntry : myRootModel.getContentEntries())
		{
			for(ContentFolder eachFolder : eachEntry.getFolders(LanguageContentFolderScopes.of(ProductionContentFolderTypeProvider.getInstance())))
			{
				if(isEqualOrAncestor(url, eachFolder.getUrl()))
				{
					return true;
				}
			}
		}
		return false;
	}

	public boolean isAlreadyExcluded(File f)
	{
		String url = toUrl(f.getPath()).getUrl();
		for(ContentEntry eachEntry : myRootModel.getContentEntries())
		{
			for(ContentFolder eachFolder : eachEntry.getFolders(LanguageContentFolderScopes.excluded()))
			{
				if(isEqualOrAncestor(eachFolder.getUrl(), url))
				{
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isEqualOrAncestor(String ancestor, String child)
	{
		return ancestor.equals(child) || StringUtil.startsWithConcatenation(child, ancestor, "/");
	}

	private boolean exists(String path)
	{
		return new File(toPath(path).getPath()).exists();
	}

	public void addExcludedFolder(String path)
	{
		unregisterAll(path, true, false);
		Url url = toUrl(path);
		ContentEntry e = getContentRootFor(url);
		if(e == null)
		{
			return;
		}
		if(e.getUrl().equals(url.getUrl()))
		{
			return;
		}
		e.addFolder(url.getUrl(), ExcludedContentFolderTypeProvider.getInstance());
	}

	public void unregisterAll(String path, boolean under, boolean unregisterSources)
	{
		Url url = toUrl(path);

		for(ContentEntry eachEntry : myRootModel.getContentEntries())
		{
			if(unregisterSources)
			{
				for(ContentFolder eachFolder : eachEntry.getFolders(LanguageContentFolderScopes.all(false)))
				{
					String ancestor = under ? url.getUrl() : eachFolder.getUrl();
					String child = under ? eachFolder.getUrl() : url.getUrl();
					if(isEqualOrAncestor(ancestor, child))
					{
						eachEntry.removeFolder(eachFolder);
					}
				}
			}

			for(ContentFolder eachFolder : eachEntry.getFolders(LanguageContentFolderScopes.excluded()))
			{
				String ancestor = under ? url.getUrl() : eachFolder.getUrl();
				String child = under ? eachFolder.getUrl() : url.getUrl();

				if(isEqualOrAncestor(ancestor, child))
				{
					if(eachFolder.isSynthetic())
					{
						ModuleCompilerPathsManager compilerPathsManager = ModuleCompilerPathsManager.getInstance(getModule());
						compilerPathsManager.setExcludeOutput(false);
					}
					else
					{
						eachEntry.removeFolder(eachFolder);
					}
				}
			}
		}
	}

	public boolean hasCollision(String sourceRootPath)
	{
		Url url = toUrl(sourceRootPath);

		for(ContentEntry eachEntry : myRootModel.getContentEntries())
		{
			for(ContentFolder eachFolder : eachEntry.getFolders(LanguageContentFolderScopes.of(ProductionContentFolderTypeProvider.getInstance())))
			{
				String ancestor = url.getUrl();
				String child = eachFolder.getUrl();
				if(isEqualOrAncestor(ancestor, child) || isEqualOrAncestor(child, ancestor))
				{
					return true;
				}
			}

			for(ContentFolder eachFolder : eachEntry.getFolders(LanguageContentFolderScopes.excluded()))
			{
				String ancestor = url.getUrl();
				String child = eachFolder.getUrl();

				if(isEqualOrAncestor(ancestor, child) || isEqualOrAncestor(child, ancestor))
				{
					return true;
				}
			}
		}

		return false;
	}

	public void useModuleOutput(String productionPath, String testPath)
	{
		ModuleCompilerPathsManager compilerPathsManager = ModuleCompilerPathsManager.getInstance(getModule());

		compilerPathsManager.setInheritedCompilerOutput(false);

		// production output
		compilerPathsManager.setCompilerOutputUrl(ProductionContentFolderTypeProvider.getInstance(), toUrl(productionPath).getUrl());
		compilerPathsManager.setCompilerOutputUrl(ProductionResourceContentFolderTypeProvider.getInstance(), toUrl(productionPath).getUrl());

		// test output
		compilerPathsManager.setCompilerOutputUrl(TestContentFolderTypeProvider.getInstance(), toUrl(testPath).getUrl());
		compilerPathsManager.setCompilerOutputUrl(TestResourceContentFolderTypeProvider.getInstance(), toUrl(testPath).getUrl());
	}

	private Url toUrl(String path)
	{
		return toPath(path).toUrl();
	}

	public Path toPath(String path)
	{
		if(!FileUtil.isAbsolute(path))
		{
			path = new File(myMavenProject.getDirectory(), path).getPath();
		}
		return new Path(path);
	}

	public void addModuleDependency(@Nonnull String moduleName, @Nonnull DependencyScope scope, boolean testJar)
	{
		Module m = findModuleByName(moduleName);

		ModuleOrderEntry e;
		if(m != null)
		{
			e = myRootModel.addModuleOrderEntry(m);
		}
		else
		{
			e = ReadAction.compute(() -> myRootModel.addInvalidModuleEntry(moduleName));
		}

		e.setScope(scope);
		if(testJar)
		{
			e.setProductionOnTestDependency(true);
		}
	}

	@Nullable
	public Module findModuleByName(String moduleName)
	{
		return myModuleModel.findModuleByName(moduleName);
	}

	public void addSystemDependency(MavenArtifact artifact, DependencyScope scope)
	{
		assert MavenConstants.SCOPE_SYSTEM.equals(artifact.getScope());

		String libraryName = artifact.getLibraryName();

		Library library = myRootModel.getModuleLibraryTable().getLibraryByName(libraryName);
		if(library == null)
		{
			library = myRootModel.getModuleLibraryTable().createLibrary(libraryName);
		}

		LibraryOrderEntry orderEntry = myRootModel.findLibraryOrderEntry(library);
		assert orderEntry != null;
		orderEntry.setScope(scope);

		Library.ModifiableModel modifiableModel = library.getModifiableModel();
		updateUrl(modifiableModel, BinariesOrderRootType.getInstance(), artifact, null, null, true);
		modifiableModel.commit();
	}

	public void addLibraryDependency(MavenArtifact artifact, DependencyScope scope, MavenModifiableModelsProvider provider, MavenProject project)
	{
		assert !MavenConstants.SCOPE_SYSTEM.equals(artifact.getScope()); // System dependencies must be added ad module library, not as project wide library.

		String libraryName = artifact.getLibraryName();

		Library library = provider.getLibraryByName(libraryName);
		if(library == null)
		{
			library = provider.createLibrary(libraryName);
		}
		Library.ModifiableModel libraryModel = provider.getLibraryModel(library);

		updateUrl(libraryModel, BinariesOrderRootType.getInstance(), artifact, null, null, true);
		updateUrl(libraryModel, SourcesOrderRootType.getInstance(), artifact, MavenExtraArtifactType.SOURCES, project, false);
		updateUrl(libraryModel, DocumentationOrderRootType.getInstance(), artifact, MavenExtraArtifactType.DOCS, project, false);

		LibraryOrderEntry e = myRootModel.addLibraryEntry(library);
		e.setScope(scope);
	}

	private static void updateUrl(Library.ModifiableModel library, OrderRootType type, MavenArtifact artifact, MavenExtraArtifactType artifactType, MavenProject project, boolean clearAll)
	{
		String classifier = null;
		String extension = null;

		if(artifactType != null)
		{
			Pair<String, String> result = project.getClassifierAndExtension(artifact, artifactType);
			classifier = result.first;
			extension = result.second;
		}


		String newPath = artifact.getPathForExtraArtifact(classifier, extension);
		String newUrl = VirtualFileManager.constructUrl(JarArchiveFileType.INSTANCE.getProtocol(), newPath) + ArchiveFileSystem.ARCHIVE_SEPARATOR;

		boolean urlExists = false;

		for(String url : library.getUrls(type))
		{
			if(newUrl.equals(url))
			{
				urlExists = true;
				continue;
			}
			if(clearAll || isRepositoryUrl(artifact, url))
			{
				library.removeRoot(url, type);
			}
		}

		if(!urlExists)
		{
			library.addRoot(newUrl, type);
		}
	}

	private static boolean isRepositoryUrl(MavenArtifact artifact, String url)
	{
		return url.contains(artifact.getGroupId().replace('.', '/') + '/' + artifact.getArtifactId() + '/' + artifact.getBaseVersion() + '/' + artifact.getArtifactId() + '-');
	}

	public static boolean isChangedByUser(Library library)
	{
		String[] classRoots = library.getUrls(BinariesOrderRootType.getInstance());
		if(classRoots.length != 1)
		{
			return true;
		}

		String classes = classRoots[0];

		if(!classes.endsWith("!/"))
		{
			return true;
		}

		int dotPos = classes.lastIndexOf("/", classes.length() - 2 /* trim ending !/ */);
		if(dotPos == -1)
		{
			return true;
		}
		String pathToJar = classes.substring(0, dotPos);

		if(hasUserPaths(SourcesOrderRootType.getInstance(), library, pathToJar))
		{
			return true;
		}
		if(hasUserPaths(DocumentationOrderRootType.getInstance(), library, pathToJar))
		{
			return true;
		}

		return false;
	}

	private static boolean hasUserPaths(OrderRootType rootType, Library library, String pathToJar)
	{
		String[] sources = library.getUrls(rootType);
		for(String each : sources)
		{
			if(!FileUtil.startsWith(each, pathToJar))
			{
				return true;
			}
		}
		return false;
	}

	public Library findLibrary(@Nonnull final MavenArtifact artifact)
	{
		final String name = artifact.getLibraryName();
		final Ref<Library> result = Ref.create(null);
		myRootModel.orderEntries().forEachLibrary(new Processor<Library>()
		{
			@Override
			public boolean process(Library library)
			{
				if(name.equals(library.getName()))
				{
					result.set(library);
				}
				return true;
			}
		});
		return result.get();
	}

	@Deprecated // Use artifact.getLibraryName();
	public static String makeLibraryName(@Nonnull MavenArtifact artifact)
	{
		return artifact.getLibraryName();
	}

	public static boolean isMavenLibrary(@Nullable Library library)
	{
		return library != null && MavenArtifact.isMavenLibrary(library.getName());
	}

	@Nullable
	public static OrderEntry findLibraryEntry(@Nonnull Module m, @Nonnull MavenArtifact artifact)
	{
		String name = artifact.getLibraryName();
		for(OrderEntry each : ModuleRootManager.getInstance(m).getOrderEntries())
		{
			if(each instanceof LibraryOrderEntry && name.equals(((LibraryOrderEntry) each).getLibraryName()))
			{
				return each;
			}
		}
		return null;
	}

	@Nullable
	public static MavenArtifact findArtifact(@Nonnull MavenProject project, @Nullable Library library)
	{
		if(library == null)
		{
			return null;
		}

		String name = library.getName();

		if(!MavenArtifact.isMavenLibrary(name))
		{
			return null;
		}

		for(MavenArtifact each : project.getDependencies())
		{
			if(each.getLibraryName().equals(name))
			{
				return each;
			}
		}
		return null;
	}

	public void setLanguageLevel(LanguageLevel level)
	{
		try
		{
			if(level == null)
			{
				return;
			}
			final JavaMutableModuleExtensionImpl extension = myRootModel.getExtension(JavaMutableModuleExtensionImpl.class);
			assert extension != null;
			extension.getInheritableLanguageLevel().set(null, level.name());
		}
		catch(IllegalArgumentException e)
		{
			//bad value was stored
		}
	}
}
