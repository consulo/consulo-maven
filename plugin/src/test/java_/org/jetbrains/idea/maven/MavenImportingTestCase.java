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

package org.jetbrains.idea.maven;

import consulo.compiler.CompileContext;
import consulo.compiler.CompileStatusNotification;
import consulo.compiler.CompilerManager;
import consulo.content.bundle.Sdk;
import consulo.content.library.Library;
import consulo.ide.impl.compiler.CompilerWorkspaceConfiguration;
import consulo.compiler.scope.ModuleCompileScope;
import consulo.ide.impl.idea.compiler.impl.TranslatingCompilerFilesMonitorImpl;
import consulo.application.ReadAction;
import consulo.compiler.scope.CompileScope;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.application.progress.EmptyProgressIndicator;
import com.intellij.openapi.roots.*;
import consulo.ide.impl.idea.openapi.roots.impl.libraries.ProjectLibraryTable;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.internal.TestDialog;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.PathUtil;
import consulo.application.util.Semaphore;
import consulo.ui.ex.awt.UIUtil;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.ide.impl.compiler.TranslatingCompilerFilesMonitor;
import consulo.roots.ContentFolderScopes;
import consulo.roots.impl.ProductionContentFolderTypeProvider;
import consulo.roots.impl.TestContentFolderTypeProvider;
import consulo.roots.types.DocumentationOrderRootType;
import consulo.util.concurrent.AsyncResult;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.execution.*;
import consulo.maven.rt.server.common.model.MavenArtifact;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenArtifactDownloader;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MavenImportingTestCase extends MavenTestCase
{
	protected MavenProjectsTree myProjectsTree;
	protected MavenProjectsManager myProjectsManager;

	@Deprecated
	protected boolean useJps()
	{
		return false;
	}

	@Override
	protected void setUpInWriteAction() throws Exception
	{
		super.setUpInWriteAction();
		myProjectsManager = MavenProjectsManager.getInstance(myProject);
		removeFromLocalRepository("test");
	}

	@Override
	protected void tearDown() throws Exception
	{
		Messages.setTestDialog(TestDialog.DEFAULT);
		myProjectsManager.dispose();
		removeFromLocalRepository("test");

		super.tearDown();
	}

	protected void assertModules(String... expectedNames)
	{
		Module[] actual = ModuleManager.getInstance(myProject).getModules();
		List<String> actualNames = new ArrayList<String>();

		for(Module m : actual)
		{
			actualNames.add(m.getName());
		}

		assertUnorderedElementsAreEqual(actualNames, expectedNames);
	}

	protected void assertContentRoots(String moduleName, String... expectedRoots)
	{
		List<String> actual = new ArrayList<String>();
		for(ContentEntry e : getContentRoots(moduleName))
		{
			actual.add(e.getUrl());
		}

		for(int i = 0; i < expectedRoots.length; i++)
		{
			expectedRoots[i] = VfsUtil.pathToUrl(expectedRoots[i]);
		}

		assertUnorderedPathsAreEqual(actual, Arrays.asList(expectedRoots));
	}

	protected void assertSources(String moduleName, String... expectedSources)
	{
		doAssertContentFolders(moduleName, true, false, expectedSources);
	}

	protected void assertTestSources(String moduleName, String... expectedSources)
	{
		doAssertContentFolders(moduleName, true, true, expectedSources);
	}

	protected void assertExcludes(String moduleName, String... expectedExcludes)
	{
		doAssertContentFolders(moduleName, false, false, expectedExcludes);
	}

	protected void assertContentRootExcludes(String moduleName, String contentRoot, String... expectedExcudes)
	{
		doAssertContentFolders(getContentRoot(moduleName, contentRoot), false, false, expectedExcudes);
	}

	private void doAssertContentFolders(String moduleName, boolean isSource, boolean isTest, String... expected)
	{
		doAssertContentFolders(getContentRoot(moduleName), isSource, isTest, expected);
	}

	private static void doAssertContentFolders(ContentEntry e, boolean isSource, boolean isTest, String... expected)
	{
		List<String> actual = new ArrayList<String>();
		for(ContentFolder f : isSource ? e.getFolders(ContentFolderScopes.productionAndTest()) : e.getFolders(ContentFolderScopes.excluded()))
		{
			if(isSource && (isTest != (f.getType() == TestContentFolderTypeProvider.getInstance())))
			{
				continue;
			}

			String rootUrl = e.getUrl();
			String folderUrl = f.getUrl();

			if(folderUrl.startsWith(rootUrl))
			{
				int lenght = rootUrl.length() + 1;
				folderUrl = folderUrl.substring(Math.min(lenght, folderUrl.length()));
			}

			actual.add(folderUrl);
		}

		assertUnorderedPathsAreEqual(actual, Arrays.asList(expected));
	}

	protected void assertModuleOutput(String moduleName, String output, String testOutput)
	{
		ModuleCompilerPathsManager e = getCompilerExtension(moduleName);

		assertFalse(e.isInheritedCompilerOutput());
		assertEquals(output, getAbsolutePath(e.getCompilerOutputUrl(ProductionContentFolderTypeProvider.getInstance())));
		assertEquals(testOutput, getAbsolutePath(e.getCompilerOutputUrl(TestContentFolderTypeProvider.getInstance())));
	}

	private static String getAbsolutePath(String path)
	{
		path = VfsUtil.urlToPath(path);
		path = PathUtil.getCanonicalPath(path);
		return FileUtil.toSystemIndependentName(path);
	}

	protected void assertProjectOutput(String module)
	{
		assertTrue(getCompilerExtension(module).isInheritedCompilerOutput());
	}

	private ModuleCompilerPathsManager getCompilerExtension(String module)
	{
		ModuleRootManager m = getRootManager(module);
		return ModuleCompilerPathsManager.getInstance(m.getModule());
	}

	protected void assertModuleLibDep(String moduleName, String depName)
	{
		assertModuleLibDep(moduleName, depName, null);
	}

	protected void assertModuleLibDep(String moduleName, String depName, String classesPath)
	{
		assertModuleLibDep(moduleName, depName, classesPath, null, null);
	}

	protected void assertModuleLibDep(String moduleName, String depName, String classesPath, String sourcePath, String javadocPath)
	{
		LibraryOrderEntry lib = getModuleLibDep(moduleName, depName);

		assertModuleLibDepPath(lib, OrderRootType.CLASSES, classesPath == null ? null : Collections.singletonList(classesPath));
		assertModuleLibDepPath(lib, OrderRootType.SOURCES, sourcePath == null ? null : Collections.singletonList(sourcePath));
		assertModuleLibDepPath(lib, DocumentationOrderRootType.getInstance(), javadocPath == null ? null : Collections.singletonList(javadocPath));
	}

	protected void assertModuleLibDep(String moduleName, String depName, List<String> classesPaths, List<String> sourcePaths, List<String> javadocPaths)
	{
		LibraryOrderEntry lib = getModuleLibDep(moduleName, depName);

		assertModuleLibDepPath(lib, OrderRootType.CLASSES, classesPaths);
		assertModuleLibDepPath(lib, OrderRootType.SOURCES, sourcePaths);
		assertModuleLibDepPath(lib, DocumentationOrderRootType.getInstance(), javadocPaths);
	}

	private void assertModuleLibDepPath(LibraryOrderEntry lib, OrderRootType type, List<String> paths)
	{
		if(paths == null)
		{
			return;
		}
		assertUnorderedPathsAreEqual(Arrays.asList(lib.getUrls(type)), paths);
		// also check the library because it may contain slight different set of urls (e.g. with duplicates)
		assertUnorderedPathsAreEqual(Arrays.asList(lib.getLibrary().getUrls(type)), paths);
	}

	protected void assertModuleLibDepScope(String moduleName, String depName, DependencyScope scope)
	{
		LibraryOrderEntry dep = getModuleLibDep(moduleName, depName);
		assertEquals(scope, dep.getScope());
	}

	private LibraryOrderEntry getModuleLibDep(String moduleName, String depName)
	{
		return getModuleDep(moduleName, depName, LibraryOrderEntry.class);
	}

	protected void assertModuleLibDeps(String moduleName, String... expectedDeps)
	{
		assertModuleDeps(moduleName, LibraryOrderEntry.class, expectedDeps);
	}

	protected void assertExportedDeps(String moduleName, String... expectedDeps)
	{
		final List<String> actual = new ArrayList<String>();

		getRootManager(moduleName).orderEntries().withoutSdk().withoutModuleSourceEntries().exportedOnly().process(new RootPolicy<Object>()
		{
			@Override
			public Object visitModuleOrderEntry(ModuleOrderEntry e, Object value)
			{
				actual.add(e.getModuleName());
				return null;
			}

			@Override
			public Object visitLibraryOrderEntry(LibraryOrderEntry e, Object value)
			{
				actual.add(e.getLibraryName());
				return null;
			}
		}, null);

		assertOrderedElementsAreEqual(actual, expectedDeps);
	}

	protected void assertModuleModuleDeps(String moduleName, String... expectedDeps)
	{
		assertModuleDeps(moduleName, ModuleOrderEntry.class, expectedDeps);
	}

	private void assertModuleDeps(String moduleName, Class clazz, String... expectedDeps)
	{
		assertOrderedElementsAreEqual(collectModuleDepsNames(moduleName, clazz), expectedDeps);
	}

	protected void assertModuleModuleDepScope(String moduleName, String depName, DependencyScope scope)
	{
		ModuleOrderEntry dep = getModuleModuleDep(moduleName, depName);
		assertEquals(scope, dep.getScope());
	}

	private ModuleOrderEntry getModuleModuleDep(String moduleName, String depName)
	{
		return getModuleDep(moduleName, depName, ModuleOrderEntry.class);
	}

	private List<String> collectModuleDepsNames(String moduleName, Class clazz)
	{
		List<String> actual = new ArrayList<String>();

		for(OrderEntry e : getRootManager(moduleName).getOrderEntries())
		{
			if(clazz.isInstance(e))
			{
				actual.add(e.getPresentableName());
			}
		}
		return actual;
	}

	private <T> T getModuleDep(String moduleName, String depName, Class<T> clazz)
	{
		T dep = null;

		for(OrderEntry e : getRootManager(moduleName).getOrderEntries())
		{
			if(clazz.isInstance(e) && e.getPresentableName().equals(depName))
			{
				dep = (T) e;
			}
		}
		assertNotNull("Dependency not found: " + depName + "\namong: " + collectModuleDepsNames(moduleName, clazz), dep);
		return dep;
	}

	public void assertProjectLibraries(String... expectedNames)
	{
		List<String> actualNames = new ArrayList<String>();
		for(Library each : consulo.ide.impl.idea.openapi.roots.impl.libraries.ProjectLibraryTable.getInstance(myProject).getLibraries())
		{
			String name = each.getName();
			actualNames.add(name == null ? "<unnamed>" : name);
		}
		assertUnorderedElementsAreEqual(actualNames, expectedNames);
	}

	protected void assertModuleGroupPath(String moduleName, String... expected)
	{
		String[] path = ModuleManager.getInstance(myProject).getModuleGroupPath(getModule(moduleName));

		if(expected.length == 0)
		{
			assertNull(path);
		}
		else
		{
			assertNotNull(path);
			assertOrderedElementsAreEqual(Arrays.asList(path), expected);
		}
	}

	protected Module getModule(final String name)
	{
		return ReadAction.compute(() -> {
			Module m = ModuleManager.getInstance(myProject).findModuleByName(name);
			assertNotNull("Module " + name + " not found", m);
			return m;
		});
	}

	private ContentEntry getContentRoot(String moduleName)
	{
		ContentEntry[] ee = getContentRoots(moduleName);
		List<String> roots = new ArrayList<String>();
		for(ContentEntry e : ee)
		{
			roots.add(e.getUrl());
		}

		String message = "Several content roots found: [" + StringUtil.join(roots, ", ") + "]";
		assertEquals(message, 1, ee.length);

		return ee[0];
	}

	private ContentEntry getContentRoot(String moduleName, String path)
	{
		for(ContentEntry e : getContentRoots(moduleName))
		{
			if(e.getUrl().equals(VfsUtil.pathToUrl(path)))
			{
				return e;
			}
		}
		throw new AssertionError("content root not found");
	}

	public ContentEntry[] getContentRoots(String moduleName)
	{
		return getRootManager(moduleName).getContentEntries();
	}

	private ModuleRootManager getRootManager(String module)
	{
		return ModuleRootManager.getInstance(getModule(module));
	}

	protected void importProject(@NonNls String xml) throws IOException
	{
		createProjectPom(xml);
		importProject();
	}

	protected void importProject()
	{
		importProjectWithProfiles();
	}

	protected void importProjectWithProfiles(String... profiles)
	{
		doImportProjects(Collections.singletonList(myProjectPom), profiles);
	}

	protected void importProject(VirtualFile file)
	{
		importProjects(file);
	}

	protected void importProjects(VirtualFile... files)
	{
		doImportProjects(Arrays.asList(files));
	}

	private void doImportProjects(final List<VirtualFile> files, String... profiles)
	{
		initProjectsManager(false);

		readProjects(files, profiles);

		UIUtil.invokeAndWaitIfNeeded(new Runnable()
		{
			@Override
			public void run()
			{
				myProjectsManager.waitForResolvingCompletion();
				myProjectsManager.scheduleImportInTests(files);
				myProjectsManager.importProjects();
			}
		});

		for(MavenProject each : myProjectsTree.getProjects())
		{
			if(each.hasReadingProblems())
			{
				System.out.println(each + " has problems: " + each.getProblems());
			}
		}
	}

	protected void readProjects(List<VirtualFile> files, String... profiles)
	{
		myProjectsManager.resetManagedFilesAndProfilesInTests(files, new MavenExplicitProfiles(Arrays.asList(profiles)));
		waitForReadingCompletion();
	}

	protected void updateProjectsAndImport(VirtualFile... files)
	{
		readProjects(files);
		myProjectsManager.performScheduledImportInTests();
	}

	protected void initProjectsManager(boolean enableEventHandling)
	{
		myProjectsManager.initForTests();
		myProjectsTree = myProjectsManager.getProjectsTreeForTests();
		if(enableEventHandling)
		{
			myProjectsManager.listenForExternalChanges();
		}
	}

	protected void scheduleResolveAll()
	{
		myProjectsManager.scheduleResolveAllInTests();
	}

	protected void waitForReadingCompletion()
	{
		UIUtil.invokeAndWaitIfNeeded(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					myProjectsManager.waitForReadingCompletion();
				}
				catch(Exception e)
				{
					throw new RuntimeException(e);
				}
			}
		});
	}

	protected void readProjects()
	{
		readProjects(myProjectsManager.getProjectsFiles());
	}

	protected void readProjects(VirtualFile... files)
	{
		List<MavenProject> projects = new ArrayList<MavenProject>();
		for(VirtualFile each : files)
		{
			projects.add(myProjectsManager.findProject(each));
		}
		myProjectsManager.forceUpdateProjects(projects);
		waitForReadingCompletion();
	}

	protected void resolveDependenciesAndImport()
	{
		UIUtil.invokeAndWaitIfNeeded(new Runnable()
		{
			@Override
			public void run()
			{
				myProjectsManager.waitForResolvingCompletion();
				myProjectsManager.performScheduledImportInTests();
			}
		});
	}

	protected void resolveFoldersAndImport()
	{
		myProjectsManager.scheduleFoldersResolveForAllProjects();
		myProjectsManager.waitForFoldersResolvingCompletion();
		UIUtil.invokeAndWaitIfNeeded(new Runnable()
		{
			@Override
			public void run()
			{
				myProjectsManager.performScheduledImportInTests();
			}
		});
	}

	protected void resolvePlugins()
	{
		myProjectsManager.waitForPluginsResolvingCompletion();
	}

	protected void downloadArtifacts()
	{
		downloadArtifacts(myProjectsManager.getProjects(), null);
	}

	protected MavenArtifactDownloader.DownloadResult downloadArtifacts(Collection<MavenProject> projects, List<MavenArtifact> artifacts)
	{
		final MavenArtifactDownloader.DownloadResult[] unresolved = new MavenArtifactDownloader.DownloadResult[1];

		AsyncResult<MavenArtifactDownloader.DownloadResult> result = AsyncResult.undefined();
		result.doWhenDone(unresolvedArtifacts -> unresolved[0] = unresolvedArtifacts);

		myProjectsManager.scheduleArtifactsDownloading(projects, artifacts, true, true, result);
		myProjectsManager.waitForArtifactsDownloadingCompletion();

		return unresolved[0];
	}

	protected void performPostImportTasks()
	{
		myProjectsManager.waitForPostImportTasksCompletion();
	}

	protected void executeGoal(String relativePath, String goal)
	{
		VirtualFile dir = myProjectRoot.findFileByRelativePath(relativePath);

		MavenRunnerParameters rp = new MavenRunnerParameters(true, dir.getPath(), Arrays.asList(goal), Collections.emptyList());
		MavenRunnerSettings rs = new MavenRunnerSettings();
		MavenExecutor e = new MavenExternalExecutor(myProject, rp, getMavenGeneralSettings(), rs, new SoutMavenConsole());

		e.execute(new EmptyProgressIndicator());
	}

	protected void removeFromLocalRepository(String relativePath) throws IOException
	{
		FileUtil.delete(new File(getRepositoryPath(), relativePath));
	}

	protected void setupJdkForModules(String... moduleNames)
	{
		for(String each : moduleNames)
		{
			setupJdkForModule(each);
		}
	}

	protected Sdk setupJdkForModule(final String moduleName)
	{
		final Sdk sdk = createJdk("Java 1.5");
		//ModuleRootModificationUtil.setModuleSdk(getModule(moduleName), sdk);
		return sdk;
	}

	protected static Sdk createJdk(String versionName)
	{
		return IdeaTestUtil.getMockJdk17(versionName);
	}

	protected void compileModules(final String... moduleNames) throws Exception
	{
		final List<consulo.module.Module> modules = new ArrayList<Module>();

		UIUtil.invokeAndWaitIfNeeded(new Runnable()
		{
			@Override
			public void run()
			{
				for(String each : moduleNames)
				{
					setupJdkForModule(each);
					modules.add(getModule(each));
				}
			}
		});

		CompilerWorkspaceConfiguration.getInstance(myProject).CLEAR_OUTPUT_DIRECTORY = true;
		//CompilerManagerImpl.testSetup();

		List<VirtualFile> roots = Arrays.asList(ProjectRootManager.getInstance(myProject).getContentRoots());
		TranslatingCompilerFilesMonitor.getInstance().scanSourceContent(new TranslatingCompilerFilesMonitorImpl.ProjectRef(myProject), roots, roots.size(), true);

		final CompileScope scope = new ModuleCompileScope(myProject, modules.toArray(new Module[modules.size()]), false);

		final Semaphore semaphore = new Semaphore();
		semaphore.down();
		UIUtil.invokeAndWaitIfNeeded(new Runnable()
		{
			@Override
			public void run()
			{
				CompilerManager.getInstance(myProject).make(scope, new CompileStatusNotification()
				{
					@Override
					public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext)
					{
						//assertFalse(aborted);
						//assertEquals(collectMessages(compileContext, CompilerMessageCategory.ERROR), 0, errors);
						//assertEquals(collectMessages(compileContext, CompilerMessageCategory.WARNING), 0, warnings);
						semaphore.up();
					}
				});
			}
		});
		while(!semaphore.waitFor(100))
		{
			if(SwingUtilities.isEventDispatchThread())
			{
				UIUtil.dispatchAllInvocationEvents();
			}
		}
		if(SwingUtilities.isEventDispatchThread())
		{
			UIUtil.dispatchAllInvocationEvents();
		}
	}

	protected static AtomicInteger configConfirmationForYesAnswer()
	{
		final AtomicInteger counter = new AtomicInteger();
		Messages.setTestDialog(new TestDialog()
		{
			@Override
			public int show(String message)
			{
				counter.set(counter.get() + 1);
				return 0;
			}
		});
		return counter;
	}

	protected static AtomicInteger configConfirmationForNoAnswer()
	{
		final AtomicInteger counter = new AtomicInteger();
		Messages.setTestDialog(new TestDialog()
		{
			@Override
			public int show(String message)
			{
				counter.set(counter.get() + 1);
				return 1;
			}
		});
		return counter;
	}
}
