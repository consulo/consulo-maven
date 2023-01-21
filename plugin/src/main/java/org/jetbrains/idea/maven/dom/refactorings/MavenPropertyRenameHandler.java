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
package org.jetbrains.idea.maven.dom.refactorings;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.editor.LangDataKeys;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.editor.refactoring.rename.PsiElementRenameHandler;
import consulo.language.editor.refactoring.rename.RenameDialog;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import org.jetbrains.idea.maven.dom.references.MavenTargetUtil;

import javax.annotation.Nonnull;

@ExtensionImpl
public class MavenPropertyRenameHandler extends PsiElementRenameHandler
{
	@Override
	public boolean isAvailableOnDataContext(DataContext context)
	{
		return findTarget(context) != null;
	}

	@Override
	public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext)
	{
		invoke(project, PsiElement.EMPTY_ARRAY, dataContext);
	}

	@Override
	public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext)
	{
		PsiElement element = elements.length == 1 ? elements[0] : null;
		if(element == null)
		{
			element = findTarget(dataContext);
		}

		RenameDialog dialog = new RenameDialog(project, element, null, dataContext.getData(PlatformDataKeys.EDITOR));
		if(ApplicationManager.getApplication().isUnitTestMode())
		{
			String name = dataContext.getData(DEFAULT_NAME);
			dialog.performRename(name);
			dialog.close(DialogWrapper.OK_EXIT_CODE);
		}
		else
		{
			dialog.show();
		}
	}

	private static PsiElement findTarget(DataContext context)
	{
		return MavenTargetUtil.getRefactorTarget(context.getData(PlatformDataKeys.EDITOR), context.getData(LangDataKeys.PSI_FILE));
	}
}
