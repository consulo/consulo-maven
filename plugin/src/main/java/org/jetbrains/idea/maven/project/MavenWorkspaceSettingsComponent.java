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
package org.jetbrains.idea.maven.project;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;

@Singleton
@State(name = "MavenImportPreferences", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class MavenWorkspaceSettingsComponent implements PersistentStateComponent<MavenWorkspaceSettings>
{
	private MavenWorkspaceSettings mySettings = new MavenWorkspaceSettings();

	private final Project myProject;

	@Inject
	public MavenWorkspaceSettingsComponent(Project project)
	{
		myProject = project;
	}

	public static MavenWorkspaceSettingsComponent getInstance(Project project)
	{
		return ServiceManager.getService(project, MavenWorkspaceSettingsComponent.class);
	}

	@Override
	@Nonnull
	public MavenWorkspaceSettings getState()
	{
		MavenExplicitProfiles profiles = MavenProjectsManager.getInstance(myProject).getExplicitProfiles();
		mySettings.setEnabledProfiles(profiles.getEnabledProfiles());
		mySettings.setDisabledProfiles(profiles.getDisabledProfiles());
		return mySettings;
	}

	@Override
	public void loadState(MavenWorkspaceSettings state)
	{
		mySettings = state;
	}

	public MavenWorkspaceSettings getSettings()
	{
		return mySettings;
	}
}
