/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.language.editor.CommonDataKeys;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.module.content.ProjectFileIndex;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import consulo.dataContext.DataContext;
import consulo.language.editor.LangDataKeys;
import consulo.language.editor.PlatformDataKeys;
import consulo.module.Module;
import consulo.project.Project;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;

public class MavenActionUtil
{
	private MavenActionUtil()
	{
	}

	public static boolean hasProject(DataContext context)
	{
		return context.getData(CommonDataKeys.PROJECT) != null;
	}

	@Nonnull
	public static Project getProject(DataContext context)
	{
		return context.getData(CommonDataKeys.PROJECT);
	}

	public static boolean isMavenizedProject(DataContext context)
	{
		Project project = context.getData(CommonDataKeys.PROJECT);
		return project != null && MavenProjectsManager.getInstance(project).isMavenizedProject();
	}

	@Nullable
	public static MavenProject getMavenProject(DataContext context)
	{
		MavenProject result;
		final MavenProjectsManager manager = getProjectsManager(context);

		final VirtualFile file = context.getData(PlatformDataKeys.VIRTUAL_FILE);
		if(file != null)
		{
			result = manager.findProject(file);
			if(result != null)
			{
				return result;
			}
		}

		Module module = getModule(context);
		if(module != null)
		{
			result = manager.findProject(module);
			if(result != null)
			{
				return result;
			}
		}

		return null;
	}

	@Nullable
	private static Module getModule(DataContext context)
	{
		final Module module = context.getData(LangDataKeys.MODULE);
		return module != null ? module : context.getData(LangDataKeys.MODULE_CONTEXT);
	}

	@Nonnull
	public static MavenProjectsManager getProjectsManager(DataContext context)
	{
		return MavenProjectsManager.getInstance(getProject(context));
	}

	public static boolean isMavenProjectFile(VirtualFile file)
	{
		return file != null && !file.isDirectory() && MavenConstants.POM_XML.equals(file.getName());
	}

	public static List<MavenProject> getMavenProjects(DataContext context)
	{
		Project project = context.getData(CommonDataKeys.PROJECT);
		if(project == null)
		{
			return Collections.emptyList();
		}

		VirtualFile[] virtualFiles = context.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
		if(virtualFiles == null || virtualFiles.length == 0)
		{
			return Collections.emptyList();
		}

		MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
		if(!projectsManager.isMavenizedProject())
		{
			return Collections.emptyList();
		}

		Set<MavenProject> res = new LinkedHashSet<MavenProject>();

		ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

		for(VirtualFile file : virtualFiles)
		{
			MavenProject mavenProject;

			if(file.isDirectory())
			{
				VirtualFile contentRoot = fileIndex.getContentRootForFile(file);
				if(!file.equals(contentRoot))
				{
					return Collections.emptyList();
				}

				Module module = fileIndex.getModuleForFile(file);
				if(module == null || !projectsManager.isMavenizedModule(module))
				{
					return Collections.emptyList();
				}

				mavenProject = projectsManager.findProject(module);
			}
			else
			{
				mavenProject = projectsManager.findProject(file);
			}

			if(mavenProject == null)
			{
				return Collections.emptyList();
			}

			res.add(mavenProject);
		}

		return new ArrayList<MavenProject>(res);
	}

	public static List<VirtualFile> getMavenProjectsFiles(DataContext context)
	{
		return MavenUtil.collectFiles(getMavenProjects(context));
	}
}
