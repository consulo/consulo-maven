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
package org.jetbrains.idea.maven.utils;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.ReadAction;
import consulo.fileEditor.FileEditorManager;
import consulo.ide.ServiceManager;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.ui.ex.awt.util.MergingUpdateQueue;
import consulo.ui.ex.awt.util.Update;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.editor.DaemonCodeAnalyzer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import consulo.maven.rt.server.common.server.NativeMavenProjectHolder;

import java.util.List;

@Singleton
@ServiceAPI(value = ComponentScope.PROJECT, lazy = false)
@ServiceImpl
public class MavenRehighlighter extends MavenSimpleProjectComponent
{
	private MergingUpdateQueue myQueue;

	@Inject
	MavenRehighlighter(Project project)
	{
		super(project);

		myQueue = new MergingUpdateQueue(getClass().getSimpleName(), 1000, true, MergingUpdateQueue.ANY_COMPONENT, myProject, null, true);
		myQueue.setPassThrough(false);

		MavenProjectsManager m = MavenProjectsManager.getInstance(myProject);

		m.addManagerListener(new MavenProjectsManager.Listener()
		{
			@Override
			public void activated()
			{
				rehighlight(myProject);
			}

			@Override
			public void projectsScheduled()
			{
			}

			@Override
			public void importAndResolveScheduled()
			{
			}
		});

		m.addProjectsTreeListener(new MavenProjectsTree.Listener()
		{
			@Override
			public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted)
			{
				for(Pair<MavenProject, MavenProjectChanges> each : updated)
				{
					rehighlight(myProject, each.first);
				}
			}

			@Override
			public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
										NativeMavenProjectHolder nativeMavenProject)
			{
				rehighlight(myProject, projectWithChanges.first);
			}

			@Override
			public void pluginsResolved(MavenProject project)
			{
				rehighlight(myProject, project);
			}

			@Override
			public void foldersResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges)
			{
				rehighlight(myProject, projectWithChanges.first);
			}

			@Override
			public void artifactsDownloaded(MavenProject project)
			{
				rehighlight(myProject, project);
			}
		});
	}

	public static void rehighlight(final Project project)
	{
		rehighlight(project, null);
	}

	public static void rehighlight(final Project project, final MavenProject mavenProject)
	{
		ReadAction.run(() ->
		{
			if(project.isDisposed())
			{
				return;
			}
			ServiceManager.getService(project, MavenRehighlighter.class).myQueue.queue(new MyUpdate(project, mavenProject));
		});
	}

	private static class MyUpdate extends Update
	{
		private final Project myProject;
		private final MavenProject myMavenProject;

		public MyUpdate(Project project, MavenProject mavenProject)
		{
			super(project);
			myProject = project;
			myMavenProject = mavenProject;
		}

		@Override
		public void run()
		{
			if(myMavenProject == null)
			{
				for(VirtualFile each : FileEditorManager.getInstance(myProject).getOpenFiles())
				{
					doRehighlightMavenFile(each);
				}
			}
			else
			{
				doRehighlightMavenFile(myMavenProject.getFile());
			}
		}

		private void doRehighlightMavenFile(VirtualFile file)
		{
			Document doc = FileDocumentManager.getInstance().getCachedDocument(file);
			if(doc == null)
			{
				return;
			}
			PsiFile psi = PsiDocumentManager.getInstance(myProject).getCachedPsiFile(doc);
			if(psi == null)
			{
				return;
			}
			if(!MavenDomUtil.isMavenFile(psi))
			{
				return;
			}

			DaemonCodeAnalyzer daemon = DaemonCodeAnalyzer.getInstance(myProject);
			daemon.restart(psi);
		}

		@Override
		public boolean canEat(Update update)
		{
			return myMavenProject == null || myMavenProject == ((MyUpdate) update).myMavenProject;
		}
	}
}
