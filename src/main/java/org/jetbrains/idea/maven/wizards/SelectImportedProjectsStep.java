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

import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.ProjectBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.projectImport.ProjectImportWizardStep;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import consulo.annotations.RequiredDispatchThread;
import consulo.maven.importProvider.MavenImportModuleContext;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class SelectImportedProjectsStep extends ProjectImportWizardStep
{
	private final JPanel panel;
	protected final ElementsChooser<MavenProject> fileChooser;
	private final JCheckBox openModuleSettingsCheckBox;
	private final MavenImportModuleContext myContext;

	public SelectImportedProjectsStep(MavenImportModuleContext context, WizardContext wizardContext)
	{
		super(wizardContext);
		myContext = context;
		fileChooser = new ElementsChooser<MavenProject>(true)
		{
			@Override
			protected String getItemText(@NotNull MavenProject item)
			{
				return getElementText(item);
			}

			@Override
			protected Icon getItemIcon(@NotNull final MavenProject item)
			{
				return getElementIcon(item);
			}
		};

		panel = new JPanel(new GridLayoutManager(3, 1, JBUI.emptyInsets(), -1, -1));

		panel.add(fileChooser, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
				GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null));

		final AnAction selectAllAction = new AnAction(RefactoringBundle.message("select.all.button"))
		{
			@RequiredDispatchThread
			@Override
			public void actionPerformed(@NotNull AnActionEvent e)
			{
				fileChooser.setAllElementsMarked(true);
			}
		};
		final AnAction unselectAllAction = new AnAction(RefactoringBundle.message("unselect.all.button"))
		{
			@RequiredDispatchThread
			@Override
			public void actionPerformed(@NotNull AnActionEvent e)
			{
				fileChooser.setAllElementsMarked(false);
			}
		};
		final JComponent actionToolbar = ActionManager.getInstance().createButtonToolbar(ActionPlaces.UNKNOWN, new DefaultActionGroup(selectAllAction, unselectAllAction));
		panel.add(actionToolbar, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints
				.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK, null, null, null));

		openModuleSettingsCheckBox = new JCheckBox(IdeBundle.message("project.import.show.settings.after"));
		panel.add(openModuleSettingsCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_SOUTH, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints
				.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));
	}

	@Nullable
	protected Icon getElementIcon(final MavenProject item)
	{
		return null;
	}

	protected abstract String getElementText(final MavenProject item);

	@Override
	public JComponent getComponent()
	{
		return panel;
	}

	protected boolean isElementEnabled(MavenProject element)
	{
		return true;
	}

	@Override
	public void updateStep(WizardContext context)
	{
		fileChooser.clear();
		for(MavenProject element : getContext().getList())
		{
			boolean isEnabled = isElementEnabled(element);
			fileChooser.addElement(element, isEnabled && getContext().isMarked(element));
			if(!isEnabled)
			{
				fileChooser.disableElement(element);
			}
		}

		fileChooser.setBorder(IdeBorderFactory.createTitledBorder(IdeBundle.message("project.import.select.title", ProjectBundle.message("maven.name")), false));
		openModuleSettingsCheckBox.setSelected(getContext().isOpenProjectSettingsAfter());
	}

	@Override
	public boolean validate(@NotNull WizardContext context) throws ConfigurationException
	{
		getContext().setList(fileChooser.getMarkedElements());
		if(fileChooser.getMarkedElements().size() == 0)
		{
			throw new ConfigurationException("Nothing found to import", "Unable to proceed");
		}
		return true;
	}

	@Override
	public void updateDataModel()
	{
	}

	@Override
	public void onStepLeaving(WizardContext context)
	{
		super.onStepLeaving(context);
		getContext().setOpenProjectSettingsAfter(openModuleSettingsCheckBox.isSelected());
	}

	public MavenImportModuleContext getContext()
	{
		return myContext;
	}
}
