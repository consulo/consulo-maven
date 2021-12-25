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

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.TextFieldCompletionProvider;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import consulo.awt.TargetAWT;
import consulo.ide.ui.FileChooserTextBoxBuilder;
import consulo.maven.icon.MavenIconGroup;
import consulo.ui.TextBoxWithExtensions;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.idea.maven.execution.cmd.ParametersListLexer;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenRunnerParametersPanel
{
	private EditorTextField myGoalsEditor;
	private EditorTextField myProfilesEditor;
	private JCheckBox myResolveToWorkspaceCheckBox;

	private FileChooserTextBoxBuilder.Controller myWorkingDirectory;
	private FormBuilder myFormBuilder = FormBuilder.createFormBuilder();

	@RequiredUIAccess
	public MavenRunnerParametersPanel(@Nonnull final Project project)
	{
		FileChooserTextBoxBuilder workDirBuilder = FileChooserTextBoxBuilder.create(project);
		workDirBuilder.dialogTitle(RunnerBundle.message("maven.select.maven.project.file"));
		workDirBuilder.fileChooserDescriptor(new FileChooserDescriptor(false, true, false, false, false, false)
		{
			@RequiredUIAccess
			@Override
			public boolean isFileSelectable(VirtualFile file)
			{
				if(!super.isFileSelectable(file))
				{
					return false;
				}
				return file.findChild(MavenConstants.POM_XML) != null;
			}
		});

		myWorkingDirectory = workDirBuilder.build();

		JComponent workTextField = (JComponent) TargetAWT.to(myWorkingDirectory.getComponent());
		if(workTextField instanceof JTextField)
		{
			// TODO [VISTALL] dirty hack with old UI form builder which change filling by cols option
			((JTextField) workTextField).setColumns(0);
		}
		myFormBuilder.addLabeledComponent("Working directory", workTextField);

		if(!project.isDefault())
		{
			TextFieldCompletionProvider profilesCompletionProvider = new TextFieldCompletionProvider(true)
			{
				@Override
				protected final void addCompletionVariants(@Nonnull String text, int offset, @Nonnull String prefix, @Nonnull CompletionResultSet result)
				{
					MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
					for(String profile : manager.getAvailableProfiles())
					{
						result.addElement(LookupElementBuilder.create(ParametersListUtil.join(profile)));
					}
				}

				@Nonnull
				@Override
				protected String getPrefix(@Nonnull String currentTextPrefix)
				{
					ParametersListLexer lexer = new ParametersListLexer(currentTextPrefix);
					while(lexer.nextToken())
					{
						if(lexer.getTokenEnd() == currentTextPrefix.length())
						{
							String prefix = lexer.getCurrentToken();
							if(prefix.startsWith("-") || prefix.startsWith("!"))
							{
								prefix = prefix.substring(1);
							}
							return prefix;
						}
					}

					return "";
				}
			};

			myGoalsEditor = new MavenArgumentsCompletionProvider(project).createEditor(project);
			myFormBuilder.addLabeledComponent("Command line", myGoalsEditor);

			myProfilesEditor = profilesCompletionProvider.createEditor(project);
			myFormBuilder.addLabeledComponent("Profiles (separated with space)", myProfilesEditor);
			JLabel label = new JBLabel("add prefix '-' to disable profile, e.g. '-test'");
			label.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
			label.setForeground(JBColor.GRAY);

			myFormBuilder.addComponentToRightColumn(label);
		}

		myResolveToWorkspaceCheckBox = new JBCheckBox("Resolve Workspace artifacts");
		myResolveToWorkspaceCheckBox.setToolTipText("In case of multi-project workspace, dependencies will be looked for in the workspace first, and only after that in local repository.");

		myFormBuilder.addComponent(myResolveToWorkspaceCheckBox);

		myWorkingDirectory.getComponent().addFirstExtension(new TextBoxWithExtensions.Extension(false, MavenIconGroup.mavenLogoTransparent(), MavenIconGroup.mavenLogo(), clickEvent -> {
			MavenSelectProjectPopup.buildPopup(MavenProjectsManager.getInstance(project), mavenProject -> {
				myWorkingDirectory.setValue(mavenProject.getDirectory());
			}).show(new RelativePoint(MouseInfo.getPointerInfo().getLocation()));
		}));
	}

	@Nonnull
	public JComponent createComponent()
	{
		return myFormBuilder.getPanel();
	}

	public void disposeUIResources()
	{
	}

	public String getDisplayName()
	{
		return RunnerBundle.message("maven.runner.parameters.title");
	}

	protected void setData(final MavenRunnerParameters data)
	{
		data.setWorkingDirPath(myWorkingDirectory.getValue());
		data.setGoals(ParametersListUtil.parse(myGoalsEditor.getText()));
		data.setResolveToWorkspace(myResolveToWorkspaceCheckBox.isSelected());

		Map<String, Boolean> profilesMap = new LinkedHashMap<>();

		List<String> profiles = ParametersListUtil.parse(myProfilesEditor.getText());

		for(String profile : profiles)
		{
			Boolean isEnabled = true;
			if(profile.startsWith("-") || profile.startsWith("!"))
			{
				profile = profile.substring(1);
				if(profile.isEmpty())
				{
					continue;
				}

				isEnabled = false;
			}

			profilesMap.put(profile, isEnabled);
		}
		data.setProfilesMap(profilesMap);
	}

	protected void getData(final MavenRunnerParameters data)
	{
		myWorkingDirectory.setValue(data.getWorkingDirPath());
		myGoalsEditor.setText(ParametersList.join(data.getGoals()));
		myResolveToWorkspaceCheckBox.setSelected(data.isResolveToWorkspace());

		ParametersList parametersList = new ParametersList();

		for(Map.Entry<String, Boolean> entry : data.getProfilesMap().entrySet())
		{
			String profileName = entry.getKey();

			if(!entry.getValue())
			{
				profileName = '-' + profileName;
			}

			parametersList.add(profileName);
		}

		myProfilesEditor.setText(parametersList.getParametersString());
	}
}
