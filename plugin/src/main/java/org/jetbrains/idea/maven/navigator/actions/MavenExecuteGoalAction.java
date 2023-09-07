/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.navigator.actions;

import consulo.content.bundle.Sdk;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.maven.MavenNotificationGroup;
import consulo.process.cmd.ParametersList;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationType;
import consulo.project.ui.notification.Notifications;
import consulo.project.ui.notification.event.NotificationListener;
import consulo.language.editor.CommonDataKeys;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jetbrains.idea.maven.execution.*;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenSettings;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.annotation.Nonnull;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MavenExecuteGoalAction extends DumbAwareAction
{
	@RequiredUIAccess
	@Override
	public void actionPerformed(@Nonnull final AnActionEvent e)
	{
		final Project project = e.getRequiredData(CommonDataKeys.PROJECT);

		ExecuteMavenGoalHistoryService historyService = ExecuteMavenGoalHistoryService.getInstance(project);

		MavenExecuteGoalDialog dialog = new MavenExecuteGoalDialog(project, historyService.getHistory());

		String lastWorkingDirectory = historyService.getWorkDirectory();
		if(lastWorkingDirectory.length() == 0)
		{
			lastWorkingDirectory = obtainAppropriateWorkingDirectory(project);
		}

		dialog.setWorkDirectory(lastWorkingDirectory);

		if(StringUtil.isEmptyOrSpaces(historyService.getCanceledCommand()))
		{
			if(historyService.getHistory().size() > 0)
			{
				dialog.setGoals(historyService.getHistory().get(0));
			}
		}
		else
		{
			dialog.setGoals(historyService.getCanceledCommand());
		}

		if(!dialog.showAndGet())
		{
			historyService.setCanceledCommand(dialog.getGoals());
			return;
		}

		historyService.setCanceledCommand(null);

		String goals = dialog.getGoals();
		goals = goals.trim();
		if(goals.startsWith("mvn "))
		{
			goals = goals.substring("mvn ".length()).trim();
		}

		String workDirectory = dialog.getWorkDirectory();

		historyService.addCommand(goals, workDirectory);

		MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);

		Pair<File, Sdk> mavenHome = MavenUtil.resolveMavenHome(projectsManager.getGeneralSettings().getMavenBundleName());
		if(mavenHome == null)
		{
			Notification notification = new Notification(MavenNotificationGroup.ROOT, "Failed to execute goal", RunnerBundle.message("external.maven.home.no.default.with.fix"),
					NotificationType.ERROR, new NotificationListener.Adapter()
			{
				@Override
				protected void hyperlinkActivated(@Nonnull Notification notification, @Nonnull HyperlinkEvent e)
				{
					ShowSettingsUtil.getInstance().showSettingsDialog(project, MavenSettings.DISPLAY_NAME);
				}
			});

			Notifications.Bus.notify(notification, project);
			return;
		}

		MavenRunnerParameters parameters = new MavenRunnerParameters(true, workDirectory, Arrays.asList(ParametersList.parse(goals)), Collections.<String>emptyList());

		MavenGeneralSettings generalSettings = new MavenGeneralSettings();
		generalSettings.setMavenBundleName(mavenHome.getSecond().getName());

		MavenRunnerSettings runnerSettings = MavenRunner.getInstance(project).getSettings().clone();
		runnerSettings.setMavenProperties(new LinkedHashMap<String, String>());
		runnerSettings.setSkipTests(false);

		MavenRunConfigurationType.runConfiguration(project, parameters, generalSettings, runnerSettings, null);
	}

	private static String obtainAppropriateWorkingDirectory(@Nonnull Project project)
	{
		List<MavenProject> rootProjects = MavenProjectsManager.getInstance(project).getRootProjects();
		if(rootProjects.isEmpty())
		{
			return "";
		}

		return rootProjects.get(0).getDirectory();
	}
}
