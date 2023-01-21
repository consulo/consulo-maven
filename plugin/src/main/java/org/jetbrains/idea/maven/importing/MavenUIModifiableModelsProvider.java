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

import consulo.application.Application;
import consulo.compiler.artifact.ModifiableArtifactModel;
import consulo.content.library.Library;
import consulo.content.library.LibraryTable;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.ModulesConfiguratorImpl;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import consulo.ide.setting.module.LibrariesConfigurator;
import consulo.ide.setting.module.ModulesConfigurator;
import consulo.module.ModifiableModuleModel;
import consulo.module.Module;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.project.Project;
import consulo.ui.ModalityState;

public class MavenUIModifiableModelsProvider extends MavenBaseModifiableModelsProvider
{
	private final ModifiableModuleModel myModel;
	private final ModulesConfiguratorImpl myModulesConfigurator;
	private final ModifiableArtifactModel myModifiableArtifactModel;
	private final LibrariesModifiableModel myLibrariesModel;

	public MavenUIModifiableModelsProvider(Project project,
										   ModifiableModuleModel model,
										   ModulesConfigurator modulesConfigurator,
										   LibrariesConfigurator librariesConfigurator,
										   ModifiableArtifactModel modifiableArtifactModel)
	{
		super(project);
		myModel = model;
		myModulesConfigurator = (ModulesConfiguratorImpl) modulesConfigurator;
		myModifiableArtifactModel = modifiableArtifactModel;

		myLibrariesModel = (LibrariesModifiableModel) librariesConfigurator.getProjectLibrariesProvider().getModifiableModel();
	}

	@Override
	protected ModifiableArtifactModel doGetArtifactModel()
	{
		return myModifiableArtifactModel;
	}

	@Override
	protected ModifiableModuleModel doGetModuleModel()
	{
		return myModel;
	}

	@Override
	protected ModifiableRootModel doGetRootModel(Module module)
	{
		return myModulesConfigurator.getOrCreateModuleEditor(module).getModifiableRootModel();
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
		return myLibrariesModel.createLibrary(name);
	}

	@Override
	public void removeLibrary(Library library)
	{
		myLibrariesModel.removeLibrary(library);
	}

	@Override
	protected Library.ModifiableModel doGetLibraryModel(Library library)
	{
		return myLibrariesModel.getLibraryModifiableModel(library);
	}

	@Override
	public void commit()
	{
		processExternalArtifactDependencies();
	}

	@Override
	public void dispose()
	{
	}

	@Override
	public ModalityState getModalityStateForQuestionDialogs()
	{
		return Application.get().getDefaultModalityState();
	}
}
