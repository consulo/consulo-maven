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
package org.jetbrains.idea.maven.tasks;

import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.execution.ParametersListUtil;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.util.concurrent.AsyncResult;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import icons.MavenIcons;
import org.jetbrains.idea.maven.execution.MavenEditGoalDialog;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenBeforeRunTasksProvider extends BeforeRunTaskProvider<MavenBeforeRunTask>
{
	public static final Key<MavenBeforeRunTask> ID = Key.create("Maven.BeforeRunTask");
	private final Project myProject;

	public MavenBeforeRunTasksProvider(Project project)
	{
		myProject = project;
	}

	@Nonnull
	@Override
	public Key<MavenBeforeRunTask> getId()
	{
		return ID;
	}

	@Nonnull
	@Override
	public String getName()
	{
		return TasksBundle.message("maven.tasks.before.run.empty");
	}

	@Override
	public Image getIcon()
	{
		return MavenIcons.MavenLogo;
	}

	@Nonnull
	@Override
	public String getDescription(MavenBeforeRunTask task)
	{
		MavenProject mavenProject = getMavenProject(task);
		if(mavenProject == null)
		{
			return TasksBundle.message("maven.tasks.before.run.empty");
		}

		String desc = mavenProject.getDisplayName() + ": " + StringUtil.notNullize(task.getGoal()).trim();
		return TasksBundle.message("maven.tasks.before.run", desc);
	}

	@Nullable
	private MavenProject getMavenProject(MavenBeforeRunTask task)
	{
		String pomXmlPath = task.getProjectPath();
		if(StringUtil.isEmpty(pomXmlPath))
		{
			return null;
		}

		VirtualFile file = LocalFileSystem.getInstance().findFileByPath(pomXmlPath);
		if(file == null)
		{
			return null;
		}

		return MavenProjectsManager.getInstance(myProject).findProject(file);
	}

	@Override
	public boolean isConfigurable()
	{
		return true;
	}

	@Override
	public MavenBeforeRunTask createTask(RunConfiguration runConfiguration)
	{
		return new MavenBeforeRunTask();
	}

	@Nonnull
	@RequiredUIAccess
	@Override
	public AsyncResult<Void> configureTask(RunConfiguration runConfiguration, MavenBeforeRunTask task)
	{
		MavenEditGoalDialog dialog = new MavenEditGoalDialog(myProject, Arrays.asList("aaa", "adasdas"));

		dialog.setTitle(TasksBundle.message("maven.tasks.select.goal.title"));

		if(task.getGoal() == null)
		{
			// just created empty task.
			MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
			List<MavenProject> rootProjects = projectsManager.getRootProjects();
			if(rootProjects.size() > 0)
			{
				dialog.setSelectedMavenProject(rootProjects.get(0));
			}
			else
			{
				dialog.setSelectedMavenProject(null);
			}
		}
		else
		{
			dialog.setGoals(task.getGoal());
			MavenProject mavenProject = getMavenProject(task);
			if(mavenProject != null)
			{
				dialog.setSelectedMavenProject(mavenProject);
			}
			else
			{
				dialog.setSelectedMavenProject(null);
			}
		}

		AsyncResult<Void> result = dialog.showAsync();
		result.doWhenDone(() -> {
			task.setProjectPath(dialog.getWorkDirectory() + "/pom.xml");
			task.setGoal(dialog.getGoals());
		});

		return result;
	}

	@Override
	public boolean canExecuteTask(RunConfiguration configuration, MavenBeforeRunTask task)
	{
		return task.getGoal() != null && task.getProjectPath() != null;
	}

	@Nonnull
	@Override
	public AsyncResult<Void> executeTaskAsync(UIAccess uiAccess, DataContext context, RunConfiguration configuration, ExecutionEnvironment env, MavenBeforeRunTask task)
	{
		AsyncResult<Void> result = AsyncResult.undefined();
		uiAccess.give(() ->
		{
			final Project project = context.getData(CommonDataKeys.PROJECT);
			final MavenProject mavenProject = getMavenProject(task);

			if(project == null || project.isDisposed() || mavenProject == null)
			{
				result.setRejected();
				return;
			}

			FileDocumentManager.getInstance().saveAllDocuments();

			final MavenExplicitProfiles explicitProfiles = MavenProjectsManager.getInstance(project).getExplicitProfiles();
			final MavenRunner mavenRunner = MavenRunner.getInstance(project);

			new Task.Backgroundable(project, TasksBundle.message("maven.tasks.executing"), true)
			{
				@Override
				public void run(@Nonnull ProgressIndicator indicator)
				{
					boolean value = false;
					try
					{
						MavenRunnerParameters params = new MavenRunnerParameters(true, mavenProject.getDirectory(), ParametersListUtil.parse(task.getGoal()), explicitProfiles);

						value = mavenRunner.runBatch(Collections.singletonList(params), null, null, TasksBundle.message("maven.tasks.executing"), indicator);
					}
					finally
					{
						if(value)
						{
							result.setDone();
						}
						else
						{
							result.setRejected();
						}
					}
				}

				@Override
				public boolean shouldStartInBackground()
				{
					return MavenRunner.getInstance(project).getSettings().isRunMavenInBackground();
				}

				@Override
				public void processSentToBackground()
				{
					MavenRunner.getInstance(project).getSettings().setRunMavenInBackground(true);
				}
			}.queue();
		}).doWhenRejectedWithThrowable(result::rejectWithThrowable);

		return result;
	}
}
