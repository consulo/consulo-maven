/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.java.compiler.impl.javaCompiler.javac.JavacCompilerConfiguration;
import com.intellij.java.compiler.impl.javaCompiler.javac.JpsJavaCompilerOptions;
import consulo.application.ApplicationManager;
import consulo.component.ProcessCanceledException;
import consulo.content.library.Library;
import consulo.java.impl.module.extension.JavaMutableModuleExtensionImpl;
import consulo.logging.Logger;
import consulo.maven.importing.MavenImportSession;
import consulo.maven.module.extension.MavenMutableModuleExtension;
import consulo.maven.rt.server.common.model.MavenArtifact;
import consulo.module.ModifiableModuleModel;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.layer.ModuleRootModel;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.Stack;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.importing.configurers.MavenModuleConfigurer;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.*;

public class MavenProjectImporter
{
	private static final Logger LOG = Logger.getInstance(MavenProjectImporter.class);
	private final Project myProject;
	private final MavenProjectsTree myProjectsTree;
	private final Map<VirtualFile, Module> myFileToModuleMapping;
	private volatile Map<MavenProject, MavenProjectChanges> myProjectsToImportWithChanges;
	private volatile Set<MavenProject> myAllProjects;
	private final boolean myImportModuleGroupsRequired;
	private final MavenModifiableModelsProvider myModelsProvider;
	private final MavenImportingSettings myImportingSettings;

	private final ModifiableModuleModel myModuleModel;

	private final List<consulo.module.Module> myCreatedModules = new ArrayList<Module>();

	private final Map<MavenProject, consulo.module.Module> myMavenProjectToModule = new HashMap<MavenProject, Module>();
	private final Map<MavenProject, String> myMavenProjectToModuleName = new HashMap<MavenProject, String>();
	private final Map<MavenProject, String> myMavenProjectToModulePath = new HashMap<MavenProject, String>();

	public MavenProjectImporter(Project p,
								MavenProjectsTree projectsTree,
								Map<VirtualFile, Module> fileToModuleMapping,
								Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges,
								boolean importModuleGroupsRequired,
								MavenModifiableModelsProvider modelsProvider,
								MavenImportingSettings importingSettings)
	{
		myProject = p;
		myProjectsTree = projectsTree;
		myFileToModuleMapping = fileToModuleMapping;
		myProjectsToImportWithChanges = projectsToImportWithChanges;
		myImportModuleGroupsRequired = importModuleGroupsRequired;
		myModelsProvider = modelsProvider;
		myImportingSettings = importingSettings;

		myModuleModel = modelsProvider.getModuleModel();
	}

	@Nullable
	public List<MavenProjectsProcessorTask> importProject()
	{
		List<MavenProjectsProcessorTask> postTasks = new ArrayList<MavenProjectsProcessorTask>();

		boolean hasChanges = false;

		// in the case projects are changed during importing we must memorise them
		myAllProjects = new LinkedHashSet<MavenProject>(myProjectsTree.getProjects());
		myAllProjects.addAll(myProjectsToImportWithChanges.keySet()); // some projects may already have been removed from the tree

		myProjectsToImportWithChanges = collectProjectsToImport(myProjectsToImportWithChanges);

		mapMavenProjectsToModulesAndNames();

		if(myProject.isDisposed())
		{
			return null;
		}

		final boolean projectsHaveChanges = projectsToImportHaveChanges();
		if(projectsHaveChanges)
		{
			hasChanges = true;
			importModules(postTasks);
			scheduleRefreshResolvedArtifacts(postTasks);
		}

		if(projectsHaveChanges || myImportModuleGroupsRequired)
		{
			hasChanges = true;
			configModuleGroups();
		}

		if(myProject.isDisposed())
		{
			return null;
		}

		try
		{
			boolean modulesDeleted = deleteObsoleteModules();
			hasChanges |= modulesDeleted;
			if(hasChanges)
			{
				removeUnusedProjectLibraries();
			}
		}
		catch(ProcessCanceledException e)
		{
			throw e;
		}
		catch(Exception e)
		{
			disposeModifiableModels();
			LOG.error(e);
			return null;
		}

		if(hasChanges)
		{
			MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable()
			{
				@Override
				public void run()
				{
					myModelsProvider.commit();

					if(projectsHaveChanges)
					{
						removeOutdatedCompilerConfigSettings();

						for(MavenProject mavenProject : myAllProjects)
						{
							Module module = myMavenProjectToModule.get(mavenProject);
							if(module != null && module.isDisposed())
							{
								module = null;
							}

							for(MavenModuleConfigurer configurer : MavenModuleConfigurer.getConfigurers())
							{
								configurer.configure(mavenProject, myProject, module);
							}
						}
					}
				}
			});
		}
		else
		{
			disposeModifiableModels();
		}

		return postTasks;
	}

	private void disposeModifiableModels()
	{
		MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable()
		{
			@Override
			public void run()
			{
				myModelsProvider.dispose();
			}
		});
	}

	private boolean projectsToImportHaveChanges()
	{
		for(MavenProjectChanges each : myProjectsToImportWithChanges.values())
		{
			if(each.hasChanges())
			{
				return true;
			}
		}
		return false;
	}

	private Map<MavenProject, MavenProjectChanges> collectProjectsToImport(Map<MavenProject, MavenProjectChanges> projectsToImport)
	{
		Map<MavenProject, MavenProjectChanges> result = new HashMap<MavenProject, MavenProjectChanges>(projectsToImport);
		result.putAll(collectNewlyCreatedProjects()); // e.g. when 'create modules fro aggregators' setting changes

		Set<MavenProject> allProjectsToImport = result.keySet();
		Set<MavenProject> selectedProjectsToImport = selectProjectsToImport(allProjectsToImport);

		Iterator<MavenProject> it = allProjectsToImport.iterator();
		while(it.hasNext())
		{
			if(!selectedProjectsToImport.contains(it.next()))
			{
				it.remove();
			}
		}

		return result;
	}

	private Map<MavenProject, MavenProjectChanges> collectNewlyCreatedProjects()
	{
		Map<MavenProject, MavenProjectChanges> result = new HashMap<MavenProject, MavenProjectChanges>();

		for(MavenProject each : myAllProjects)
		{
			consulo.module.Module module = myFileToModuleMapping.get(each.getFile());
			if(module == null)
			{
				result.put(each, MavenProjectChanges.ALL);
			}
		}

		return result;
	}

	private Set<MavenProject> selectProjectsToImport(Collection<MavenProject> originalProjects)
	{
		Set<MavenProject> result = new HashSet<MavenProject>();
		for(MavenProject each : originalProjects)
		{
			if(!shouldCreateModuleFor(each))
			{
				continue;
			}
			result.add(each);
		}
		return result;
	}

	private boolean shouldCreateModuleFor(MavenProject project)
	{
		if(myProjectsTree.isIgnored(project))
		{
			return false;
		}
		return !project.isAggregator() || myImportingSettings.isCreateModulesForAggregators();
	}

	private static String formatProjectsWithModules(List<Pair<MavenProject, Module>> projectsWithModules)
	{
		return StringUtil.join(projectsWithModules, each ->
		{
			MavenProject project = each.first;
			Module module = each.second;
			return module.getName() +
					"' for Maven project " +
					project.getMavenId().getDisplayString();
		}, "<br>");
	}

	private boolean deleteObsoleteModules()
	{
		final List<Module> obsoleteModules = collectObsoleteModules();
		if(obsoleteModules.isEmpty())
		{
			return false;
		}

		setMavenizedModules(obsoleteModules, false);

		final int[] result = new int[1];
		MavenUtil.invokeAndWait(myProject, myModelsProvider.getModalityStateForQuestionDialogs(), new Runnable()
		{
			@Override
			public void run()
			{
				result[0] = Messages.showYesNoDialog(myProject, ProjectBundle.message("maven.import.message.delete.obsolete", formatModules(obsoleteModules)),
						ProjectBundle.message("maven.project.import.title"), Messages.getQuestionIcon());
			}
		});

		if(result[0] == Messages.NO)
		{
			return false;// NO
		}

		for(Module each : obsoleteModules)
		{
			if(!each.isDisposed())
			{
				myModuleModel.disposeModule(each);
			}
		}

		return true;
	}

	private List<Module> collectObsoleteModules()
	{
		List<consulo.module.Module> remainingModules = new ArrayList<consulo.module.Module>();
		Collections.addAll(remainingModules, myModuleModel.getModules());

		for(MavenProject each : selectProjectsToImport(myAllProjects))
		{
			remainingModules.remove(myMavenProjectToModule.get(each));
		}

		List<Module> obsolete = new ArrayList<Module>();
		final MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);
		for(consulo.module.Module each : remainingModules)
		{
			if(manager.isMavenizedModule(each))
			{
				obsolete.add(each);
			}
		}
		return obsolete;
	}

	private static String formatModules(final Collection<consulo.module.Module> modules)
	{
		StringBuilder res = new StringBuilder();

		int i = 0;
		for(Module module : modules)
		{
			res.append('\'').append(module.getName()).append("'\n");

			if(++i > 20)
			{
				break;
			}
		}

		if(i > 20)
		{
			res.append("\n ... and other ").append(modules.size() - 20).append(" modules");
		}

		return res.toString();
	}

	private static void doRefreshFiles(Set<File> files)
	{
		LocalFileSystem.getInstance().refreshIoFiles(files);
	}

	private void scheduleRefreshResolvedArtifacts(List<MavenProjectsProcessorTask> postTasks)
	{
		// We have to refresh all the resolved artifacts manually in order to
		// update all the VirtualFilePointers. It is not enough to call
		// VirtualFileManager.refresh() since the newly created files will be only
		// picked by FS when FileWatcher finishes its work. And in the case of import
		// it doesn't finish in time.
		// I couldn't manage to write a test for this since behaviour of VirtualFileManager
		// and FileWatcher differs from real-life execution.

		List<MavenArtifact> artifacts = new ArrayList<MavenArtifact>();
		for(MavenProject each : myProjectsToImportWithChanges.keySet())
		{
			artifacts.addAll(each.getDependencies());
		}

		final Set<File> files = new HashSet<File>();
		for(MavenArtifact each : artifacts)
		{
			if(each.isResolved())
			{
				files.add(each.getFile());
			}
		}

		if(ApplicationManager.getApplication().isUnitTestMode())
		{
			doRefreshFiles(files);
		}
		else
		{
			postTasks.add(new MavenProjectsProcessorTask()
			{
				@Override
				public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator) throws MavenProcessCanceledException
				{
					indicator.setText("Refreshing files...");
					doRefreshFiles(files);
				}
			});
		}
	}

	private void mapMavenProjectsToModulesAndNames()
	{
		for(MavenProject each : myAllProjects)
		{
			consulo.module.Module module = myFileToModuleMapping.get(each.getFile());
			if(module != null)
			{
				myMavenProjectToModule.put(each, module);
			}
		}

		MavenModuleNameMapper.map(myAllProjects, myMavenProjectToModule, myMavenProjectToModuleName, myMavenProjectToModulePath, myImportingSettings.getDedicatedModuleDir());
	}

	private void removeOutdatedCompilerConfigSettings()
	{
		final JpsJavaCompilerOptions javacOptions = JavacCompilerConfiguration.getInstance(myProject);
		String options = javacOptions.ADDITIONAL_OPTIONS_STRING;
		options = options.replaceFirst("(-target (\\S+))", ""); // Old IDEAs saved
		javacOptions.ADDITIONAL_OPTIONS_STRING = options;
	}

	private void importModules(final List<MavenProjectsProcessorTask> tasks)
	{
		Map<MavenProject, MavenProjectChanges> projectsWithChanges = myProjectsToImportWithChanges;

		Set<MavenProject> projectsWithNewlyCreatedModules = new HashSet<MavenProject>();

		for(MavenProject each : projectsWithChanges.keySet())
		{
			if(ensureModuleCreated(each))
			{
				projectsWithNewlyCreatedModules.add(each);
			}
		}

		List<consulo.module.Module> modulesToMavenize = new ArrayList<Module>();
		List<MavenModuleImporter> importers = new ArrayList<MavenModuleImporter>();

		MavenImportSession session = new MavenImportSession();

		for(Map.Entry<MavenProject, MavenProjectChanges> each : projectsWithChanges.entrySet())
		{
			MavenProject project = each.getKey();
			Module module = myMavenProjectToModule.get(project);
			boolean isNewModule = projectsWithNewlyCreatedModules.contains(project);

			MavenModuleImporter moduleImporter = createModuleImporter(module, project, each.getValue());
			modulesToMavenize.add(module);
			importers.add(moduleImporter);

			moduleImporter.config(isNewModule, session);
		}

		for(MavenProject project : myAllProjects)
		{
			if(!projectsWithChanges.containsKey(project))
			{
				Module module = myMavenProjectToModule.get(project);
				if(module == null)
				{
					continue;
				}

				importers.add(createModuleImporter(module, project, null));
			}
		}

		for(MavenModuleImporter importer : importers)
		{
			importer.preConfigFacets();
		}

		for(MavenModuleImporter importer : importers)
		{
			importer.configFacets(tasks);
		}

		setMavenizedModules(modulesToMavenize, true);
	}

	private void setMavenizedModules(final Collection<consulo.module.Module> modules, final boolean mavenized)
	{
		MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable()
		{
			@Override
			public void run()
			{
				for(Module module : modules)
				{
					final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

					final ModifiableRootModel modifiableModel = moduleRootManager.getModifiableModel();
					//noinspection ConstantConditions
					modifiableModel.getExtensionWithoutCheck(JavaMutableModuleExtensionImpl.class).setEnabled(true);
					//noinspection ConstantConditions
					modifiableModel.getExtensionWithoutCheck(MavenMutableModuleExtension.class).setEnabled(mavenized);
					modifiableModel.commit();
				}
			}
		});
	}

	private boolean ensureModuleCreated(MavenProject project)
	{
		if(myMavenProjectToModule.get(project) != null)
		{
			return false;
		}

		final String path = myMavenProjectToModulePath.get(project);
		final String name = myMavenProjectToModuleName.get(project);


		final Module module = myModuleModel.newModule(name, path);
		myMavenProjectToModule.put(project, module);
		myCreatedModules.add(module);
		return true;
	}


	private MavenModuleImporter createModuleImporter(Module module, MavenProject mavenProject, @Nullable MavenProjectChanges changes)
	{
		return new MavenModuleImporter(module, myProjectsTree, mavenProject, changes, myMavenProjectToModuleName, myImportingSettings, myModelsProvider);
	}

	private void configModuleGroups()
	{
		if(!myImportingSettings.isCreateModuleGroups())
		{
			return;
		}

		final Stack<String> groups = new consulo.util.collection.Stack<String>();
		final boolean createTopLevelGroup = myProjectsTree.getRootProjects().size() > 1;

		myProjectsTree.visit(new MavenProjectsTree.SimpleVisitor()
		{
			int depth = 0;

			@Override
			public boolean shouldVisit(MavenProject project)
			{
				// in case some project has been added while we were importing
				return myMavenProjectToModuleName.containsKey(project);
			}

			@Override
			public void visit(MavenProject each)
			{
				depth++;

				String name = myMavenProjectToModuleName.get(each);

				if(shouldCreateGroup(each))
				{
					groups.push(ProjectBundle.message("module.group.name", name));
				}

				if(!shouldCreateModuleFor(each))
				{
					return;
				}

				consulo.module.Module module = myModuleModel.findModuleByName(name);
				if(module == null)
				{
					return;
				}
				myModuleModel.setModuleGroupPath(module, groups.isEmpty() ? null : ArrayUtil.toStringArray(groups));
			}

			@Override
			public void leave(MavenProject each)
			{
				if(shouldCreateGroup(each))
				{
					groups.pop();
				}
				depth--;
			}

			private boolean shouldCreateGroup(MavenProject project)
			{
				return !myProjectsTree.getModules(project).isEmpty() && (createTopLevelGroup || depth > 1);
			}
		});
	}

	private boolean removeUnusedProjectLibraries()
	{
		Set<Library> unusedLibraries = new HashSet<Library>();
		Collections.addAll(unusedLibraries, myModelsProvider.getAllLibraries());

		for(ModuleRootModel eachModel : collectModuleModels())
		{
			for(OrderEntry eachEntry : eachModel.getOrderEntries())
			{
				if(eachEntry instanceof LibraryOrderEntry)
				{
					unusedLibraries.remove(((LibraryOrderEntry) eachEntry).getLibrary());
				}
			}
		}

		boolean removed = false;
		for(Library each : unusedLibraries)
		{
			if(!isDisposed(each) && MavenRootModelAdapter.isMavenLibrary(each) && !MavenRootModelAdapter.isChangedByUser(each))
			{
				myModelsProvider.removeLibrary(each);
				removed = true;
			}
		}
		return removed;
	}

	private static boolean isDisposed(Library library)
	{
		return library.isDisposed();
	}

	private Collection<ModuleRootModel> collectModuleModels()
	{
		Map<Module, ModuleRootModel> rootModels = new HashMap<Module, ModuleRootModel>();
		for(MavenProject each : myProjectsToImportWithChanges.keySet())
		{
			Module module = myMavenProjectToModule.get(each);
			ModifiableRootModel rootModel = myModelsProvider.getRootModel(module);
			rootModels.put(module, rootModel);
		}
		for(consulo.module.Module each : myModuleModel.getModules())
		{
			if(rootModels.containsKey(each))
			{
				continue;
			}
			rootModels.put(each, myModelsProvider.getRootModel(each));
		}
		return rootModels.values();
	}

	public List<Module> getCreatedModules()
	{
		return myCreatedModules;
	}
}
