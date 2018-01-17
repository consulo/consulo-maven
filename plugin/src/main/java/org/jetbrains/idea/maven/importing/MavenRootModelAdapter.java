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

import java.io.File;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.Path;
import org.jetbrains.idea.maven.utils.Url;
import com.intellij.ide.highlighter.JarArchiveFileType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModuleOrderEntryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.Processor;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.java.module.extension.JavaMutableModuleExtensionImpl;
import consulo.roots.ContentFolderScopes;
import consulo.roots.ContentFolderTypeProvider;
import consulo.roots.impl.ExcludedContentFolderTypeProvider;
import consulo.roots.impl.ProductionContentFolderTypeProvider;
import consulo.roots.impl.ProductionResourceContentFolderTypeProvider;
import consulo.roots.impl.TestContentFolderTypeProvider;
import consulo.roots.impl.TestResourceContentFolderTypeProvider;
import consulo.roots.impl.property.GeneratedContentFolderPropertyProvider;
import consulo.vfs.ArchiveFileSystem;

public class MavenRootModelAdapter
{

	private final MavenProject myMavenProject;
	private final ModifiableModuleModel myModuleModel;
	private final ModifiableRootModel myRootModel;

	public MavenRootModelAdapter(@NotNull MavenProject p, @NotNull Module module, final MavenModifiableModelsProvider rootModelsProvider)
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
				Module m = ((ModuleOrderEntry) e).getModule();
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
			for(ContentFolder contentFolder : each.getFolders(ContentFolderScopes.all(false)))
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
			for(ContentFolder eachFolder : eachEntry.getFolders(ContentFolderScopes.of(ProductionContentFolderTypeProvider.getInstance())))
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
			for(ContentFolder eachFolder : eachEntry.getFolders(ContentFolderScopes.excluded()))
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
				for(ContentFolder eachFolder : eachEntry.getFolders(ContentFolderScopes.all(false)))
				{
					String ancestor = under ? url.getUrl() : eachFolder.getUrl();
					String child = under ? eachFolder.getUrl() : url.getUrl();
					if(isEqualOrAncestor(ancestor, child))
					{
						eachEntry.removeFolder(eachFolder);
					}
				}
			}

			for(ContentFolder eachFolder : eachEntry.getFolders(ContentFolderScopes.excluded()))
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
			for(ContentFolder eachFolder : eachEntry.getFolders(ContentFolderScopes.of(ProductionContentFolderTypeProvider.getInstance())))
			{
				String ancestor = url.getUrl();
				String child = eachFolder.getUrl();
				if(isEqualOrAncestor(ancestor, child) || isEqualOrAncestor(child, ancestor))
				{
					return true;
				}
			}

			for(ContentFolder eachFolder : eachEntry.getFolders(ContentFolderScopes.excluded()))
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

	public void addModuleDependency(@NotNull String moduleName, @NotNull DependencyScope scope, boolean testJar)
	{
		Module m = findModuleByName(moduleName);

		ModuleOrderEntry e;
		if(m != null)
		{
			e = myRootModel.addModuleOrderEntry(m);
		}
		else
		{
			AccessToken accessToken = ReadAction.start();
			try
			{
				e = myRootModel.addInvalidModuleEntry(moduleName);
			}
			finally
			{
				accessToken.finish();
			}
		}

		e.setScope(scope);
		if(testJar)
		{
			((ModuleOrderEntryImpl) e).setProductionOnTestDependency(true);
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
		updateUrl(modifiableModel, OrderRootType.CLASSES, artifact, null, null, true);
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

		updateUrl(libraryModel, OrderRootType.CLASSES, artifact, null, null, true);
		updateUrl(libraryModel, OrderRootType.SOURCES, artifact, MavenExtraArtifactType.SOURCES, project, false);
		updateUrl(libraryModel, OrderRootType.DOCUMENTATION, artifact, MavenExtraArtifactType.DOCS, project, false);

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
		String[] classRoots = library.getUrls(OrderRootType.CLASSES);
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

		if(hasUserPaths(OrderRootType.SOURCES, library, pathToJar))
		{
			return true;
		}
		if(hasUserPaths(OrderRootType.DOCUMENTATION, library, pathToJar))
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

	public Library findLibrary(@NotNull final MavenArtifact artifact)
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
	public static String makeLibraryName(@NotNull MavenArtifact artifact)
	{
		return artifact.getLibraryName();
	}

	public static boolean isMavenLibrary(@Nullable Library library)
	{
		return library != null && MavenArtifact.isMavenLibrary(library.getName());
	}

	@Nullable
	public static OrderEntry findLibraryEntry(@NotNull Module m, @NotNull MavenArtifact artifact)
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
	public static MavenArtifact findArtifact(@NotNull MavenProject project, @Nullable Library library)
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