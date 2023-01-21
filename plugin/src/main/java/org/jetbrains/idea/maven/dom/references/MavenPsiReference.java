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
package org.jetbrains.idea.maven.dom.references;

import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.annotation.Nonnull;

public abstract class MavenPsiReference implements PsiReference
{
	protected final
	@Nonnull
	Project myProject;
	protected final
	@Nonnull
	MavenProjectsManager myProjectsManager;

	protected final
	@Nonnull
	PsiFile myPsiFile;
	protected final
	@Nonnull
	VirtualFile myVirtualFile;

	protected final
	@Nonnull
	PsiElement myElement;
	protected final
	@Nonnull
	String myText;
	protected final
	@Nonnull
	TextRange myRange;

	public MavenPsiReference(@Nonnull PsiElement element, @Nonnull String text, @Nonnull TextRange range)
	{
		myProject = element.getProject();
		myProjectsManager = MavenProjectsManager.getInstance(myProject);

		myPsiFile = element.getContainingFile().getOriginalFile();
		myVirtualFile = myPsiFile.getVirtualFile();

		myElement = element;
		myText = text;
		myRange = range;
	}

	public PsiElement getElement()
	{
		return myElement;
	}

	@Nonnull
	public String getCanonicalText()
	{
		return myText;
	}

	public TextRange getRangeInElement()
	{
		return myRange;
	}

	public boolean isReferenceTo(PsiElement element)
	{
		return getElement().getManager().areElementsEquivalent(element, resolve());
	}

	public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException
	{
		return null;
	}

	public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException
	{
		return null;
	}

	public boolean isSoft()
	{
		return true;
	}
}