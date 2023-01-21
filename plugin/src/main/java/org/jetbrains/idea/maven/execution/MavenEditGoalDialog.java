/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import consulo.application.AllIcons;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.ide.impl.idea.util.ArrayUtilRt;
import consulo.language.editor.ui.awt.EditorComboBoxEditor;
import consulo.language.editor.ui.awt.EditorComboBoxRenderer;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.editor.ui.awt.StringComboboxEditor;
import consulo.language.plain.PlainTextFileType;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.project.Project;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.util.Collection;

public class MavenEditGoalDialog extends DialogWrapper
{
	private final Project myProject;
	@Nullable
	private final Collection<String> myHistory;

	private JPanel contentPane;

	private FixedSizeButton showProjectTreeButton;
	private TextFieldWithBrowseButton workDirectoryField;

	private JPanel goalsPanel;
	private JLabel goalsLabel;
	private ComboBox goalsComboBox;
	private EditorTextField goalsEditor;

	public MavenEditGoalDialog(@Nonnull Project project)
	{
		this(project, null);
	}

	public MavenEditGoalDialog(@Nonnull Project project, @Nullable Collection<String> history)
	{
		super(project, true);
		myProject = project;
		myHistory = history;

		setTitle("Edit Maven Goal");
		setUpDialog();
		setModal(true);
		init();
	}

	private void setUpDialog()
	{
		JComponent goalComponent;
		if(myHistory == null)
		{
			goalsEditor = new EditorTextField("", myProject, PlainTextFileType.INSTANCE);
			goalComponent = goalsEditor;

			goalsLabel.setLabelFor(goalsEditor);
		}
		else
		{
			goalsComboBox = new ComboBox(ArrayUtilRt.toStringArray(myHistory));
			goalComponent = goalsComboBox;

			goalsLabel.setLabelFor(goalsComboBox);

			goalsComboBox.setLightWeightPopupEnabled(false);

			EditorComboBoxEditor editor = new StringComboboxEditor(myProject, PlainTextFileType.INSTANCE, goalsComboBox);
			goalsComboBox.setRenderer(new EditorComboBoxRenderer(editor));

			goalsComboBox.setEditable(true);
			goalsComboBox.setEditor(editor);
			goalsComboBox.setFocusable(true);

			goalsEditor = editor.getEditorComponent();
		}

		goalsPanel.add(goalComponent);

		new MavenArgumentsCompletionProvider(myProject).apply(goalsEditor);


		MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);

		showProjectTreeButton.setIcon(TargetAWT.to(AllIcons.Actions.Module));
		MavenSelectProjectPopup.attachToWorkingDirectoryField(projectsManager, workDirectoryField.getTextField(), showProjectTreeButton, goalsComboBox != null ? goalsComboBox : goalsEditor);

		workDirectoryField.addBrowseFolderListener(RunnerBundle.message("maven.select.maven.project.file"), "", myProject, new FileChooserDescriptor(false, true, false, false, false, false)
		{
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
	}

	@Nullable
	@Override
	protected ValidationInfo doValidate()
	{
		if(workDirectoryField.getText().trim().isEmpty())
		{
			return new ValidationInfo("Working directory is empty", workDirectoryField);
		}

		return null;
	}

	@Nonnull
	public String getGoals()
	{
		if(goalsComboBox != null)
		{
			return (String) goalsComboBox.getEditor().getItem();
		}
		else
		{
			return goalsEditor.getText();
		}
	}

	public void setGoals(@Nonnull String goals)
	{
		if(goalsComboBox != null)
		{
			goalsComboBox.setSelectedItem(goals);
		}

		goalsEditor.setText(goals);
	}

	@Nonnull
	public String getWorkDirectory()
	{
		return workDirectoryField.getText();
	}

	public void setWorkDirectory(@Nonnull String path)
	{
		workDirectoryField.setText(path);
	}

	public void setSelectedMavenProject(@Nullable MavenProject mavenProject)
	{
		workDirectoryField.setText(mavenProject == null ? "" : mavenProject.getDirectory());
	}

	protected JComponent createCenterPanel()
	{
		return contentPane;
	}

	public JComponent getPreferredFocusedComponent()
	{
		return goalsComboBox;
	}

}
