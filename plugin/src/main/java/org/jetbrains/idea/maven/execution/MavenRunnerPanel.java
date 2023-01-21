/* ==========================================================================
 * Copyright 2006 Mevenide Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */

package org.jetbrains.idea.maven.execution;

import com.intellij.java.language.projectRoots.JavaSdk;
import consulo.application.AllIcons;
import consulo.content.bundle.SdkTypeId;
import consulo.disposer.Disposable;
import consulo.execution.ui.awt.EnvironmentVariablesComponent;
import consulo.execution.ui.awt.RawCommandLineEditor;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import consulo.module.ui.awt.SdkComboBox;
import consulo.project.Project;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.util.lang.function.Conditions;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class MavenRunnerPanel
{
	protected final Project myProject;
	private final boolean myRunConfigurationMode;

	private JCheckBox myRunInBackgroundCheckbox;
	private RawCommandLineEditor myVMParametersEditor;
	private EnvironmentVariablesComponent myEnvVariablesComponent;
	private SdkComboBox myJdkCombo;
	private JCheckBox mySkipTestsCheckBox;
	private MavenPropertiesPanel myPropertiesPanel;

	private Map<String, String> myProperties;

	public MavenRunnerPanel(@Nonnull Project p, boolean isRunConfiguration)
	{
		myProject = p;
		myRunConfigurationMode = isRunConfiguration;
	}

	public JComponent createComponent(@Nonnull Disposable uiDiposable)
	{
		JPanel panel = new JPanel(new GridBagLayout());

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;
		c.insets.bottom = 5;

		myRunInBackgroundCheckbox = new JCheckBox("Run in background");
		myRunInBackgroundCheckbox.setMnemonic('b');
		if(!myRunConfigurationMode)
		{
			c.gridx = 0;
			c.gridy++;
			c.weightx = 1;
			c.gridwidth = GridBagConstraints.REMAINDER;

			panel.add(myRunInBackgroundCheckbox, c);
		}
		c.gridwidth = 1;

		JLabel labelVMParameters = new JLabel("VM Options:");
		labelVMParameters.setDisplayedMnemonic('v');
		labelVMParameters.setLabelFor(myVMParametersEditor = new RawCommandLineEditor());
		myVMParametersEditor.setDialogCaption(labelVMParameters.getText());

		c.gridx = 0;
		c.gridy++;
		c.weightx = 0;
		panel.add(labelVMParameters, c);

		c.gridx = 1;
		c.weightx = 1;
		c.insets.left = 10;
		panel.add(myVMParametersEditor, c);
		c.insets.left = 0;

		JLabel jdkLabel = new JLabel("JRE:");
		jdkLabel.setDisplayedMnemonic('j');
		JavaSdk javaSdkType = JavaSdk.getInstance();
		ProjectSdksModel sdksModel = new ProjectSdksModel();
		sdksModel.reset();
		myJdkCombo = new SdkComboBox(sdksModel, Conditions.<SdkTypeId>is(javaSdkType), null, "Auto Select", AllIcons.Actions.FindPlain);

		jdkLabel.setLabelFor(myJdkCombo);
		c.gridx = 0;
		c.gridy++;
		c.weightx = 0;
		panel.add(jdkLabel, c);
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.NONE;
		c.insets.left = 10;
		panel.add(myJdkCombo, c);
		c.insets.left = 0;
		c.fill = GridBagConstraints.HORIZONTAL;

		myEnvVariablesComponent = new EnvironmentVariablesComponent();
		myEnvVariablesComponent.setPassParentEnvs(true);
		myEnvVariablesComponent.setLabelLocation(BorderLayout.WEST);
		c.gridx = 0;
		c.gridy++;
		c.weightx = 1;
		c.gridwidth = 2;
		panel.add(myEnvVariablesComponent, c);
		c.gridwidth = 1;

		JPanel propertiesPanel = new JPanel(new BorderLayout());
		propertiesPanel.setBorder(IdeBorderFactory.createTitledBorder("Properties", false));

		propertiesPanel.add(mySkipTestsCheckBox = new JCheckBox("Skip tests"), BorderLayout.NORTH);
		mySkipTestsCheckBox.setMnemonic('t');

		collectProperties();
		propertiesPanel.add(myPropertiesPanel = new MavenPropertiesPanel(myProperties), BorderLayout.CENTER);
		myPropertiesPanel.getEmptyText().setText("No properties defined");

		c.gridx = 0;
		c.gridy++;
		c.weightx = c.weighty = 1;
		c.gridwidth = c.gridheight = GridBagConstraints.REMAINDER;
		c.fill = GridBagConstraints.BOTH;
		panel.add(propertiesPanel, c);

		return panel;
	}

	private void collectProperties()
	{
		MavenProjectsManager s = MavenProjectsManager.getInstance(myProject);
		Map<String, String> result = new LinkedHashMap<String, String>();

		for(MavenProject each : s.getProjects())
		{
			Properties properties = each.getProperties();
			result.putAll((Map) properties);
		}

		myProperties = result;
	}

	protected void reset(MavenRunnerSettings data)
	{
		myRunInBackgroundCheckbox.setSelected(data.isRunMavenInBackground());
		myVMParametersEditor.setText(data.getVmOptions());
		mySkipTestsCheckBox.setSelected(data.isSkipTests());

		myJdkCombo.setSelectedSdk(data.getJreName());
		myPropertiesPanel.setDataFromMap(data.getMavenProperties());

		myEnvVariablesComponent.setEnvs(data.getEnvironmentProperties());
		myEnvVariablesComponent.setPassParentEnvs(data.isPassParentEnv());
	}

	protected void apply(MavenRunnerSettings data)
	{
		data.setRunMavenInBackground(myRunInBackgroundCheckbox.isSelected());
		data.setVmOptions(myVMParametersEditor.getText().trim());
		data.setSkipTests(mySkipTestsCheckBox.isSelected());
		data.setJreName(myJdkCombo.getSelectedSdkName());

		data.setMavenProperties(myPropertiesPanel.getDataAsMap());

		data.setEnvironmentProperties(myEnvVariablesComponent.getEnvs());
		data.setPassParentEnv(myEnvVariablesComponent.isPassParentEnvs());
	}

	public Project getProject()
	{
		return myProject;
	}
}
