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

import com.intellij.ide.util.MultiStateElementsChooser;
import consulo.disposer.Disposable;
import consulo.maven.importProvider.MavenImportModuleContext;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.wizard.WizardStep;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenProfileKind;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class SelectProfilesStep implements WizardStep<MavenImportModuleContext>
{
	private JPanel panel;
	private MultiStateElementsChooser<String, MavenProfileKind> profileChooser;
	private MavenProfileKindMarkStateDescriptor myMarkStateDescriptor;

	private final MavenImportModuleContext myContext;

	public SelectProfilesStep(MavenImportModuleContext context)
	{
		myContext = context;
	}

	@Override
	public boolean isVisible(MavenImportModuleContext context)
	{
		return !myContext.getProfiles().isEmpty();
	}

	public void createUIComponents()
	{
		myMarkStateDescriptor = new MavenProfileKindMarkStateDescriptor();
		profileChooser = new MultiStateElementsChooser<>(true, myMarkStateDescriptor);
	}

	@RequiredUIAccess
	@Nonnull
	@Override
	public consulo.ui.Component getComponent(@Nonnull Disposable uiDisposable)
	{
		throw new UnsupportedOperationException("desktop only");
	}

	@Override
	public JComponent getSwingComponent(@Nonnull Disposable uiDisposable)
	{
		return panel;
	}

	@Override
	public void onStepEnter(@Nonnull MavenImportModuleContext mavenImportModuleContext)
	{
		List<String> allProfiles = myContext.getProfiles();
		List<String> activatedProfiles = myContext.getActivatedProfiles();
		MavenExplicitProfiles selectedProfiles = myContext.getSelectedProfiles();
		List<String> enabledProfiles = new ArrayList<>(selectedProfiles.getEnabledProfiles());
		List<String> disabledProfiles = new ArrayList<>(selectedProfiles.getDisabledProfiles());
		enabledProfiles.retainAll(allProfiles); // mark only existing profiles
		disabledProfiles.retainAll(allProfiles); // mark only existing profiles

		myMarkStateDescriptor.setActivatedProfiles(activatedProfiles);
		profileChooser.setElements(allProfiles, null);
		profileChooser.markElements(enabledProfiles, MavenProfileKind.EXPLICIT);
		profileChooser.markElements(disabledProfiles, MavenProfileKind.NONE);
	}

	@Override
	public void onStepLeave(@Nonnull MavenImportModuleContext context)
	{
		Collection<String> activatedProfiles = myMarkStateDescriptor.getActivatedProfiles();
		MavenExplicitProfiles newSelectedProfiles = MavenExplicitProfiles.NONE.clone();
		for(Map.Entry<String, MavenProfileKind> entry : profileChooser.getElementMarkStates().entrySet())
		{
			String profile = entry.getKey();
			MavenProfileKind profileKind = entry.getValue();
			switch(profileKind)
			{
				case NONE:
					if(activatedProfiles.contains(profile))
					{
						newSelectedProfiles.getDisabledProfiles().add(profile);
					}
					break;
				case EXPLICIT:
					newSelectedProfiles.getEnabledProfiles().add(profile);
					break;
				case IMPLICIT:
					break;
			}
		}
		myContext.setSelectedProfiles(newSelectedProfiles);
	}

	private static class MavenProfileKindMarkStateDescriptor implements MultiStateElementsChooser.MarkStateDescriptor<String, MavenProfileKind>
	{
		private Collection<String> myActivatedProfiles = Collections.emptySet();

		public Collection<String> getActivatedProfiles()
		{
			return myActivatedProfiles;
		}

		public void setActivatedProfiles(Collection<String> activatedProfiles)
		{
			myActivatedProfiles = new HashSet<>(activatedProfiles);
		}

		@Nonnull
		@Override
		public MavenProfileKind getDefaultState(@Nonnull String element)
		{
			return myActivatedProfiles.contains(element) ? MavenProfileKind.IMPLICIT : MavenProfileKind.NONE;
		}

		@Nonnull
		@Override
		public MavenProfileKind getNextState(@Nonnull String element, @Nonnull MavenProfileKind state)
		{
			MavenProfileKind nextState;
			switch(state)
			{
				case NONE:
					nextState = MavenProfileKind.EXPLICIT;
					break;
				case EXPLICIT:
					nextState = getDefaultState(element);
					break;
				case IMPLICIT:
				default:
					nextState = MavenProfileKind.NONE;
					break;
			}
			return nextState;
		}

		@Nullable
		@Override
		public MavenProfileKind getNextState(@Nonnull Map<String, MavenProfileKind> elementsWithStates)
		{
			MavenProfileKind nextState = null;
			for(Map.Entry<String, MavenProfileKind> entry : elementsWithStates.entrySet())
			{
				MavenProfileKind nextElementState = getNextState(entry.getKey(), entry.getValue());
				if(nextState == null)
				{
					nextState = nextElementState;
				}
				else if(!nextState.equals(nextElementState))
				{
					nextState = null;
					break;
				}
			}
			return nextState;
		}

		@Override
		public boolean isMarked(@Nonnull MavenProfileKind state)
		{
			return state != MavenProfileKind.NONE;
		}

		@Nullable
		@Override
		public MavenProfileKind getMarkState(@Nullable Object value)
		{
			return value instanceof MavenProfileKind ? (MavenProfileKind) value : null;
		}

		@Nullable
		@Override
		public TableCellRenderer getMarkRenderer()
		{
			return new CheckboxTableCellRenderer();
		}
	}

	private static class CheckboxTableCellRenderer extends JCheckBox implements TableCellRenderer
	{
		public CheckboxTableCellRenderer()
		{
			setHorizontalAlignment(SwingConstants.CENTER);
			setBorder(null);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			if(isSelected)
			{
				setForeground(table.getSelectionForeground());
				super.setBackground(table.getSelectionBackground());
			}
			else
			{
				setForeground(table.getForeground());
				setBackground(table.getBackground());
			}

			MavenProfileKind state = (MavenProfileKind) value;
			setSelected(state != MavenProfileKind.NONE);
			setEnabled(state != MavenProfileKind.IMPLICIT);

			return this;
		}
	}
}
