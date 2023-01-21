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
package org.jetbrains.idea.maven.wizards;

import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.disposer.Disposable;
import consulo.ide.newModule.ui.NamePathComponent;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.maven.importProvider.MavenImportModuleContext;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.wizard.WizardStep;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.idea.maven.project.*;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MavenProjectImportStep implements WizardStep<MavenImportModuleContext>
{
	private final JPanel myPanel;
	private final NamePathComponent myRootPathComponent;
	private final MavenImportingSettingsForm myImportingSettingsForm;
	private final MavenImportModuleContext myContext;

	public MavenProjectImportStep(MavenImportModuleContext context)
	{
		myContext = context;

		myImportingSettingsForm = new MavenImportingSettingsForm(true, context.isNewProject());

		myRootPathComponent = new NamePathComponent("", ProjectBundle.message("maven.import.label.select.root"), ProjectBundle.message("maven.import.title.select.root"), "", false, false);

		JButton envSettingsButton = new JButton(ProjectBundle.message("maven.import.environment.settings"));
		envSettingsButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				ShowSettingsUtil.getInstance().editConfigurable(myPanel, new MavenEnvironmentConfigurable());
			}
		});

		myPanel = new JPanel(new GridBagLayout());

		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(4, 4, 0, 4);

		myPanel.add(myRootPathComponent, c);

		c.gridy = 1;
		c.insets = new Insets(4, 4, 0, 4);
		myPanel.add(myImportingSettingsForm.createComponent(), c);

		c.gridy = 2;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.NORTHEAST;
		c.weighty = 1;
		c.insets = new Insets(4 + envSettingsButton.getPreferredSize().height, 4, 4, 4);
		myPanel.add(envSettingsButton, c);

		myRootPathComponent.setNameComponentVisible(false);
	}

	@RequiredUIAccess
	@Nonnull
	@Override
	public Component getComponent(@Nonnull MavenImportModuleContext context, @Nonnull Disposable disposable)
	{
		throw new UnsupportedOperationException("destop only");
	}

	@RequiredUIAccess
	@Nonnull
	@Override
	public JComponent getSwingComponent(@Nonnull MavenImportModuleContext context, @Nonnull Disposable disposable)
	{
		return myPanel;
	}

	@Override
	public void onStepEnter(@Nonnull MavenImportModuleContext mavenImportModuleContext)
	{
		if(!myRootPathComponent.isPathChangedByUser())
		{
			final VirtualFile rootDirectory = myContext.getRootDirectory();
			final String path;
			if(rootDirectory != null)
			{
				path = rootDirectory.getPath();
			}
			else
			{
				path = myContext.getPath();
			}
			if(path != null)
			{
				myRootPathComponent.setPath(FileUtil.toSystemDependentName(path));
				myRootPathComponent.getPathComponent().selectAll();
			}
		}
		myImportingSettingsForm.setData(getImportingSettings());
	}

	@Override
	public void onStepLeave(@Nonnull MavenImportModuleContext mavenImportModuleContext)
	{
		MavenImportingSettings settings = getImportingSettings();
		myImportingSettingsForm.getData(settings);
		suggestProjectNameAndPath(settings.getDedicatedModuleDir(), myRootPathComponent.getPath());
		myContext.setRootDirectory(myContext.getProject(), myRootPathComponent.getPath());
	}

	protected void suggestProjectNameAndPath(final String alternativePath, final String path)
	{
		myContext.setPath(alternativePath != null && alternativePath.length() > 0 ? alternativePath : path);
		final String global = FileUtil.toSystemIndependentName(path);
		myContext.setName(global.substring(global.lastIndexOf("/") + 1));
	}

	@Override
	public JComponent getSwingPreferredFocusedComponent()
	{
		return myRootPathComponent.getPathComponent();
	}

	private MavenGeneralSettings getGeneralSettings()
	{
		return myContext.getGeneralSettings();
	}

	private MavenImportingSettings getImportingSettings()
	{
		return myContext.getImportingSettings();
	}

	class MavenEnvironmentConfigurable implements Configurable
	{
		MavenEnvironmentForm myForm = new MavenEnvironmentForm();

		@Override
		@Nls
		public String getDisplayName()
		{
			return ProjectBundle.message("maven.import.environment.settings.title");
		}

		@RequiredUIAccess
		@Override
		public JComponent createComponent(@Nonnull Disposable uiDisposable)
		{
			return myForm.createComponent(uiDisposable);
		}

		@RequiredUIAccess
		@Override
		public boolean isModified()
		{
			return myForm.isModified(getGeneralSettings());
		}

		@RequiredUIAccess
		@Override
		public void apply() throws ConfigurationException
		{
			myForm.setData(getGeneralSettings());
		}

		@RequiredUIAccess
		@Override
		public void reset()
		{
			myForm.getData(getGeneralSettings());
		}
	}
}
