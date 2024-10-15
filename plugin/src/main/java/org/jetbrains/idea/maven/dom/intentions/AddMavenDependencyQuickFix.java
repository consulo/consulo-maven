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
package org.jetbrains.idea.maven.dom.intentions;

import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import consulo.xml.util.xml.DomUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.idea.maven.project.MavenProject;

import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.regex.Pattern;

public class AddMavenDependencyQuickFix implements SyntheticIntentionAction, LowPriorityAction {
    private static final Pattern CLASSNAME_PATTERN =
        Pattern.compile("(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)*\\p{Lu}\\p{javaJavaIdentifierPart}+");

    private final PsiJavaCodeReferenceElement myRef;

    public AddMavenDependencyQuickFix(PsiJavaCodeReferenceElement ref) {
        myRef = ref;
    }

    @Nonnull
    @Override
    public String getText() {
        return "Add Maven Dependency...";
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return myRef.isValid() && MavenDomUtil.findContainingProject(file) != null && looksLikeClassName(getReferenceText());
    }

    private static boolean looksLikeClassName(@Nullable String text) {
        if (text == null) {
            return false;
        }
        //if (true) return true;
        return CLASSNAME_PATTERN.matcher(text).matches();
    }

    @Override
    public void invoke(@Nonnull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
        if (!myRef.isValid()) {
            return;
        }

        MavenProject mavenProject = MavenDomUtil.findContainingProject(file);
        if (mavenProject == null) {
            return;
        }

        final List<MavenId> ids = MavenArtifactSearchDialog.searchForClass(project, getReferenceText());
        if (ids.isEmpty()) {
            return;
        }

        final MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(project, mavenProject.getFile());
        if (model == null) {
            return;
        }

        new WriteCommandAction(project, "Add Maven Dependency", DomUtil.getFile(model)) {
            @Override
            protected void run(Result result) throws Throwable {
                for (MavenId each : ids) {
                    MavenDomUtil.createDomDependency(model, null, each);
                }
            }
        }.execute();
    }

    public String getReferenceText() {
        PsiJavaCodeReferenceElement result = myRef;
        while (true) {
            if (!(result.getParent() instanceof PsiJavaCodeReferenceElement javaCodeReferenceElement)) {
                break;
            }
            result = javaCodeReferenceElement;
        }

        return result.getQualifiedName();
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
