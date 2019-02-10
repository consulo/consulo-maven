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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

import org.jetbrains.idea.maven.project.MavenEnvironmentForm;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenImportingSettingsForm;
import org.jetbrains.idea.maven.project.ProjectBundle;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportWizardStep;
import consulo.ui.RequiredUIAccess;
import consulo.maven.importProvider.MavenImportModuleContext;

public class MavenProjectImportStep extends ProjectImportWizardStep
{
	private final JPanel myPanel;
	private final NamePathComponent myRootPathComponent;
	private final MavenImportingSettingsForm myImportingSettingsForm;
	private final MavenImportModuleContext myContext;

	public MavenProjectImportStep(MavenImportModuleContext context, WizardContext wizardContext)
	{
		super(wizardContext);
		myContext = context;

		myImportingSettingsForm = new MavenImportingSettingsForm(true, wizardContext.isCreatingNewProject());

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
		myPanel.setBorder(BorderFactory.createEtchedBorder());

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

	@Override
	public JComponent getComponent()
	{
		return myPanel;
	}

	@Override
	public void updateDataModel()
	{
		MavenImportingSettings settings = getImportingSettings();
		myImportingSettingsForm.getData(settings);
		suggestProjectNameAndPath(settings.getDedicatedModuleDir(), myRootPathComponent.getPath());
	}

	@Override
	public boolean validate(@Nonnull WizardContext wizardContext) throws ConfigurationException
	{
		updateDataModel(); // needed to make 'exhaustive search' take an effect.
		return myContext.setRootDirectory(getWizardContext().getProject(), myRootPathComponent.getPath());
	}

	@Override
	public void updateStep(WizardContext wizardContext)
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
				path = getWizardContext().getProjectFileDirectory();
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
	public JComponent getPreferredFocusedComponent()
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

	@Override
	@NonNls
	public String getHelpId()
	{
		return "reference.dialogs.new.project.import.maven.page1";
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

		@Override
		@javax.annotation.Nullable
		@NonNls
		public String getHelpTopic()
		{
			return null;
		}

		@RequiredUIAccess
		@Override
		public JComponent createComponent()
		{
			return myForm.createComponent();
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

		@RequiredUIAccess
		@Override
		public void disposeUIResources()
		{
		}
	}
}
