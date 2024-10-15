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
package org.jetbrains.idea.maven.dom.intentions;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.IdeaFileChooser;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomManager;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.localize.MavenDomLocalize;

import jakarta.annotation.Nonnull;

import java.util.function.Supplier;

@ExtensionImpl
@IntentionMetaData(ignoreId = "maven.choose.file.intention", categories = {"Java", "Maven"}, fileExtensions = "xml")
public class ChooseFileIntentionAction implements IntentionAction {
    private Supplier<VirtualFile[]> myFileChooser = null;

    @Nonnull
    @Override
    public String getText() {
        return MavenDomLocalize.intentionChooseFile().get();
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!MavenDomUtil.isMavenFile(file)) {
            return false;
        }
        MavenDomDependency dep = getDependency(file, editor);
        return dep != null && "system".equals(dep.getScope().getStringValue());
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        final MavenDomDependency dep = getDependency(file, editor);

        final VirtualFile[] files;
        if (myFileChooser == null) {
            final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, true, false, false);
            final PsiFile currentValue = dep != null ? dep.getSystemPath().getValue() : null;
            final VirtualFile toSelect = currentValue == null ? null : currentValue.getVirtualFile();
            files = IdeaFileChooser.chooseFiles(descriptor, project, toSelect);
        }
        else {
            files = myFileChooser.get();
        }
        if (files == null || files.length == 0) {
            return;
        }

        final PsiFile selectedFile = PsiManager.getInstance(project).findFile(files[0]);
        if (selectedFile == null) {
            return;
        }

        if (dep != null) {
            new WriteCommandAction(project) {
                @Override
                protected void run(Result result) throws Throwable {
                    dep.getSystemPath().setValue(selectedFile);
                }
            }.execute();
        }
    }

    @TestOnly
    public void setFileChooser(@Nullable final Supplier<VirtualFile[]> fileChooser) {
        myFileChooser = fileChooser;
    }

    @Nullable
    private static MavenDomDependency getDependency(PsiFile file, Editor editor) {
        PsiElement el = PsiUtilCore.getElementAtOffset(file, editor.getCaretModel().getOffset());

        XmlTag tag = PsiTreeUtil.getParentOfType(el, XmlTag.class, false);
        if (tag == null) {
            return null;
        }

        DomElement dom = DomManager.getDomManager(el.getProject()).getDomElement(tag);
        if (dom == null) {
            return null;
        }

        return dom.getParentOfType(MavenDomDependency.class, false);
    }
}
