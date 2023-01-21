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

import com.intellij.java.execution.configurations.JavaCommandLineState;
import consulo.execution.DefaultExecutionResult;
import consulo.execution.ExecutionBundle;
import consulo.execution.ExecutionResult;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LocatableConfigurationBase;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.log.ui.LogConfigurationPanel;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.configuration.ui.SettingsEditorGroup;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.java.debugger.impl.GenericDebugRunnerConfiguration;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowId;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.util.xml.serializer.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MavenRunConfiguration extends LocatableConfigurationBase implements GenericDebugRunnerConfiguration
{
	private MavenSettings mySettings;

	protected MavenRunConfiguration(Project project, ConfigurationFactory factory, String name)
	{
		super(project, factory, name);
		mySettings = new MavenSettings(project);
	}

	@Override
	public MavenRunConfiguration clone()
	{
		MavenRunConfiguration clone = (MavenRunConfiguration) super.clone();
		clone.mySettings = mySettings.clone();
		return clone;
	}

	@Nonnull
	@Override
	public SettingsEditor<? extends RunConfiguration> getConfigurationEditor()
	{
		SettingsEditorGroup<MavenRunConfiguration> group = new SettingsEditorGroup<MavenRunConfiguration>();

		group.addEditor(RunnerBundle.message("maven.runner.parameters.title"), new MavenRunnerParametersSettingEditor(getProject()));
		group.addEditor(ProjectBundle.message("maven.tab.general"), new MavenGeneralSettingsEditor(getProject()));
		group.addEditor(RunnerBundle.message("maven.tab.runner"), new MavenRunnerSettingsEditor(getProject()));
		group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<MavenRunConfiguration>());
		return group;
	}

	public OwnJavaParameters createJavaParameters(@Nullable Project project) throws ExecutionException
	{
		return MavenExternalParameters.createJavaParameters(project, mySettings.myRunnerParameters, mySettings.myGeneralSettings,
				mySettings.myRunnerSettings, this);
	}

	@Override
	public RunProfileState getState(@Nonnull final Executor executor, @Nonnull final ExecutionEnvironment env) throws ExecutionException
	{
		JavaCommandLineState state = new JavaCommandLineState(env)
		{
			@Override
			protected OwnJavaParameters createJavaParameters() throws ExecutionException
			{
				return MavenRunConfiguration.this.createJavaParameters(env.getProject());
			}

			@Nonnull
			@Override
			public ExecutionResult execute(@Nonnull Executor executor, @Nonnull ProgramRunner runner) throws ExecutionException
			{
				DefaultExecutionResult res = (DefaultExecutionResult) super.execute(executor, runner);
				if(executor.getId().equals(ToolWindowId.RUN) && MavenResumeAction.isApplicable(env.getProject(), getJavaParameters(),
						MavenRunConfiguration.this))
				{
					MavenResumeAction resumeAction = new MavenResumeAction(res.getProcessHandler(), runner, env);
					res.setRestartActions(resumeAction);
				}
				return res;
			}

			@Nonnull
			@Override
			protected ProcessHandler startProcess() throws ExecutionException
			{
				ProcessHandler result = super.startProcess();
				// process always destroy recursive result.setShouldDestroyProcessRecursively(true);
				result.addProcessListener(new ProcessAdapter()
				{
					@Override
					public void processTerminated(ProcessEvent event)
					{
						updateProjectsFolders();
					}
				});
				return result;
			}
		};
		state.setConsoleBuilder(MavenConsoleImpl.createConsoleBuilder(getProject()));
		return state;
	}

	private void updateProjectsFolders()
	{
		MavenProjectsManager.getInstance(getProject()).updateProjectTargetFolders();
	}

	@Override
	@Nonnull
	public consulo.module.Module[] getModules()
	{
		return Module.EMPTY_ARRAY;
	}

	@Nullable
	public MavenGeneralSettings getGeneralSettings()
	{
		return mySettings.myGeneralSettings;
	}

	public void setGeneralSettings(@Nullable MavenGeneralSettings settings)
	{
		mySettings.myGeneralSettings = settings;
	}

	@Nullable
	public MavenRunnerSettings getRunnerSettings()
	{
		return mySettings.myRunnerSettings;
	}

	public void setRunnerSettings(@Nullable MavenRunnerSettings settings)
	{
		mySettings.myRunnerSettings = settings;
	}

	public MavenRunnerParameters getRunnerParameters()
	{
		return mySettings.myRunnerParameters;
	}

	public void setRunnerParameters(MavenRunnerParameters p)
	{
		mySettings.myRunnerParameters = p;
	}

	@Override
	public void readExternal(Element element) throws InvalidDataException
	{
		super.readExternal(element);

		Element mavenSettingsElement = element.getChild(MavenSettings.TAG);
		if(mavenSettingsElement != null)
		{
			mySettings = XmlSerializer.deserialize(mavenSettingsElement, MavenSettings.class);
			if(mySettings == null)
			{
				mySettings = new MavenSettings();
			}

			if(mySettings.myRunnerParameters == null)
			{
				mySettings.myRunnerParameters = new MavenRunnerParameters();
			}

			// fix old settings format
			mySettings.myRunnerParameters.fixAfterLoadingFromOldFormat();
		}
	}

	@Override
	public void writeExternal(Element element) throws WriteExternalException
	{
		super.writeExternal(element);
		element.addContent(XmlSerializer.serialize(mySettings));
	}

	@Override
	public String suggestedName()
	{
		return MavenRunConfigurationType.generateName(getProject(), mySettings.myRunnerParameters);
	}

	public static class MavenSettings implements Cloneable
	{
		public static final String TAG = "MavenSettings";

		public MavenGeneralSettings myGeneralSettings;
		public MavenRunnerSettings myRunnerSettings;
		public MavenRunnerParameters myRunnerParameters;

		/* reflection only */
		public MavenSettings()
		{
		}

		public MavenSettings(Project project)
		{
			this(null, null, new MavenRunnerParameters());
		}

		private MavenSettings(@Nullable MavenGeneralSettings cs, @Nullable MavenRunnerSettings rs, MavenRunnerParameters rp)
		{
			myGeneralSettings = cs == null ? null : cs.clone();
			myRunnerSettings = rs == null ? null : rs.clone();
			myRunnerParameters = rp.clone();
		}

		@Override
		protected MavenSettings clone()
		{
			return new MavenSettings(myGeneralSettings, myRunnerSettings, myRunnerParameters);
		}
	}
}
