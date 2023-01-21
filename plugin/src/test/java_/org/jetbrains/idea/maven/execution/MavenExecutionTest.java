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
package org.jetbrains.idea.maven.execution;

import consulo.application.util.Semaphore;
import consulo.execution.runner.ProgramRunner;
import consulo.process.event.ProcessAdapter;
import consulo.execution.ui.RunContentDescriptor;
import consulo.application.WriteAction;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.ui.ex.awt.UIUtil;
import consulo.disposer.Disposer;
import consulo.process.event.ProcessEvent;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings({"ConstantConditions"})
public abstract class MavenExecutionTest extends MavenImportingTestCase
{
	@Override
	protected boolean runInWriteAction()
	{
		return false;
	}

	@Override
	protected boolean runInDispatchThread()
	{
		return false;
	}

	public void testExternalExecutor() throws Exception
	{
		if(!hasMavenInstallation())
		{
			return;
		}

		VfsUtil.saveText(createProjectSubFile("src/main/java/A.java"), "public class A {}");

		WriteAction.run(() ->
		{
			createProjectPom("<groupId>test</groupId>" +
					"<artifactId>project</artifactId>" +
					"<version>1</version>");
		});

		assertFalse(new File(getProjectPath(), "target").exists());

		execute(new MavenRunnerParameters(true, getProjectPath(), Arrays.asList("compile"), new ArrayList<>()));

		assertTrue(new File(getProjectPath(), "target").exists());
	}

	public void testUpdatingExcludedFoldersAfterExecution() throws Exception
	{
		if(!hasMavenInstallation())
		{
			return;
		}

		WriteAction.run(() ->
		{
			createStdProjectFolders();

			importProject("<groupId>test</groupId>" +
					"<artifactId>project</artifactId>" +
					"<version>1</version>");

			createProjectSubDirs("target/generated-sources/foo",
					"target/bar");
		});

		assertModules("project");
		assertExcludes("project", "target");

		MavenRunnerParameters params = new MavenRunnerParameters(true, getProjectPath(), Arrays.asList("compile"), new ArrayList<>());
		execute(params);

		SwingUtilities.invokeAndWait(new Runnable()
		{
			@Override
			public void run()
			{
			}
		});

		assertSources("project",
				"src/main/java",
				"src/main/resources",
				"target/generated-sources/foo");

		assertExcludes("project",
				"target/bar",
				"target/classes",
				"target/classes"); // output dirs are collected twice for exclusion and for compiler output
	}

	private void execute(final MavenRunnerParameters params)
	{
		final Semaphore sema = new Semaphore();
		sema.down();
		UIUtil.invokeLaterIfNeeded(new Runnable()
		{
			@Override
			public void run()
			{
				MavenRunConfigurationType.runConfiguration(
						myProject, params, getMavenGeneralSettings(),
						new MavenRunnerSettings(),
						new ProgramRunner.Callback()
						{
							@Override
							public void processStarted(final RunContentDescriptor descriptor)
							{
								descriptor.getProcessHandler().addProcessListener(new ProcessAdapter()
								{
									@Override
									public void processTerminated(ProcessEvent event)
									{
										sema.up();
										UIUtil.invokeLaterIfNeeded(new Runnable()
										{
											@Override
											public void run()
											{
												Disposer.dispose(descriptor);
											}
										});
									}
								});
							}
						});
			}
		});
		sema.waitFor();
	}
}
