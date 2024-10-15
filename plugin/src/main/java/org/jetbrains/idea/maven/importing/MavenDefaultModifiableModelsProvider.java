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

import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.compiler.artifact.ArtifactManager;
import consulo.compiler.artifact.ModifiableArtifactModel;
import consulo.content.library.Library;
import consulo.content.library.LibraryTable;
import consulo.module.ModifiableModuleModel;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModifiableModelCommitter;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.project.Project;
import consulo.project.content.library.ProjectLibraryTable;
import consulo.ui.ModalityState;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryType;

import jakarta.annotation.Nonnull;
import java.util.Collection;

public class MavenDefaultModifiableModelsProvider extends MavenBaseModifiableModelsProvider
{
	private final LibraryTable.ModifiableModel myLibrariesModel;

	public MavenDefaultModifiableModelsProvider(Project project)
	{
		super(project);
		myLibrariesModel = ProjectLibraryTable.getInstance(myProject).getModifiableModel();
	}

	@Override
	protected ModifiableArtifactModel doGetArtifactModel()
	{
		return ReadAction.compute(() -> ArtifactManager.getInstance(myProject).createModifiableModel());
	}

	@Override
	protected ModifiableModuleModel doGetModuleModel()
	{
		return ReadAction.compute(() -> ModuleManager.getInstance(myProject).getModifiableModel());
	}

	@Override
	protected ModifiableRootModel doGetRootModel(@Nonnull final Module module)
	{
		return ReadAction.compute(() -> ModuleRootManager.getInstance(module).getModifiableModel());
	}

	@Override
	public LibraryTable.ModifiableModel getProjectLibrariesModel()
	{
		return myLibrariesModel;
	}

	@Override
	public Library[] getAllLibraries()
	{
		return myLibrariesModel.getLibraries();
	}

	@Override
	public Library getLibraryByName(String name)
	{
		return myLibrariesModel.getLibraryByName(name);
	}

	@Override
	public Library createLibrary(String name)
	{
		return myLibrariesModel.createLibrary(name, RepositoryLibraryType.getInstance().getKind());
	}

	@Override
	public void removeLibrary(Library library)
	{
		myLibrariesModel.removeLibrary(library);
	}

	@Override
	protected Library.ModifiableModel doGetLibraryModel(Library library)
	{
		return library.getModifiableModel();
	}

	@Override
	public void commit()
	{
		ProjectRootManager.getInstance(myProject).mergeRootsChangesDuring(new Runnable()
		{
			@Override
			public void run()
			{
				processExternalArtifactDependencies();
				for(Library.ModifiableModel each : myLibraryModels.values())
				{
					each.commit();
				}
				myLibrariesModel.commit();
				Collection<ModifiableRootModel> rootModels = myRootModels.values();

				ModifiableRootModel[] rootModels1 = rootModels.toArray(new ModifiableRootModel[rootModels.size()]);
				for(ModifiableRootModel model : rootModels1)
				{
					assert !model.isDisposed() : "Already disposed: " + model;
				}
				ModifiableModelCommitter.getInstance(myProject).multiCommit(rootModels1, myModuleModel);

				if(myArtifactModel != null)
				{
					myArtifactModel.commit();
				}
			}
		});
	}

	@Override
	public void dispose()
	{
		for(ModifiableRootModel each : myRootModels.values())
		{
			each.dispose();
		}
		myModuleModel.dispose();
		if(myArtifactModel != null)
		{
			myArtifactModel.dispose();
		}
	}

	@Override
	public ModalityState getModalityStateForQuestionDialogs()
	{
		return Application.get().getNoneModalityState();
	}
}
