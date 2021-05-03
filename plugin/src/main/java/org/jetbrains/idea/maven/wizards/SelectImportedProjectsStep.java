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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.actionSystem.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import consulo.disposer.Disposable;
import consulo.maven.importProvider.MavenImportModuleContext;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.ui.wizard.WizardStep;
import consulo.ui.wizard.WizardStepValidationException;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.ProjectBundle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class SelectImportedProjectsStep implements WizardStep<MavenImportModuleContext>
{
	private final JPanel panel;
	protected final ElementsChooser<MavenProject> fileChooser;
	protected final MavenImportModuleContext myContext;

	public SelectImportedProjectsStep(MavenImportModuleContext context)
	{
		myContext = context;
		fileChooser = new ElementsChooser<MavenProject>(true)
		{
			@Override
			protected String getItemText(@Nonnull MavenProject item)
			{
				return getElementText(item);
			}

			@Override
			protected Image getItemIcon(@Nonnull final MavenProject item)
			{
				return getElementIcon(item);
			}
		};

		panel = new JPanel(new GridLayoutManager(3, 1, JBUI.emptyInsets(), -1, -1));

		panel.add(fileChooser, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
				GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null));

		final AnAction selectAllAction = new AnAction(RefactoringBundle.message("select.all.button"))
		{
			@RequiredUIAccess
			@Override
			public void actionPerformed(@Nonnull AnActionEvent e)
			{
				fileChooser.setAllElementsMarked(true);
			}
		};
		final AnAction unselectAllAction = new AnAction(RefactoringBundle.message("unselect.all.button"))
		{
			@RequiredUIAccess
			@Override
			public void actionPerformed(@Nonnull AnActionEvent e)
			{
				fileChooser.setAllElementsMarked(false);
			}
		};
		final JComponent actionToolbar = ActionManager.getInstance().createButtonToolbar(ActionPlaces.UNKNOWN, new DefaultActionGroup(selectAllAction, unselectAllAction));
		panel.add(actionToolbar, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints
				.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK, null, null, null));
	}

	@Nullable
	protected Image getElementIcon(final MavenProject item)
	{
		return null;
	}

	protected abstract String getElementText(final MavenProject item);

	@RequiredUIAccess
	@Nonnull
	@Override
	public Component getComponent(@Nonnull Disposable disposable)
	{
		throw new UnsupportedOperationException("desktop only");
	}

	@RequiredUIAccess
	@Nonnull
	@Override
	public JComponent getSwingComponent(@Nonnull Disposable disposable)
	{
		return panel;
	}

	protected boolean isElementEnabled(MavenProject element)
	{
		return true;
	}

	@Override
	public void onStepEnter(@Nonnull MavenImportModuleContext context)
	{
		fileChooser.clear();
		List<MavenProject> list = context.getList();
		if(list != null)
		{
			for(MavenProject element : list)
			{
				boolean isEnabled = isElementEnabled(element);
				fileChooser.addElement(element, isEnabled && getContext().isMarked(element));
				if(!isEnabled)
				{
					fileChooser.disableElement(element);
				}
			}
		}

		fileChooser.setBorder(IdeBorderFactory.createTitledBorder(IdeBundle.message("project.import.select.title", ProjectBundle.message("maven.name")), false));
	}

	@Override
	public void onStepLeave(@Nonnull MavenImportModuleContext context)
	{
		context.setList(fileChooser.getMarkedElements());

		updateDataModel();
	}

	@Override
	public void validateStep(@Nonnull MavenImportModuleContext context) throws WizardStepValidationException
	{
		onStepLeave(context);
		if(fileChooser.getMarkedElements().size() == 0)
		{
			throw new WizardStepValidationException("Nothing found to import");
		}
	}

	public void updateDataModel()
	{
	}

	public MavenImportModuleContext getContext()
	{
		return myContext;
	}
}
