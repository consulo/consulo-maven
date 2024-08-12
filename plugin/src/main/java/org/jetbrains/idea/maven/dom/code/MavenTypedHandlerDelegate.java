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
package org.jetbrains.idea.maven.dom.code;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.action.TypedHandlerDelegate;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;

@ExtensionImpl
public class MavenTypedHandlerDelegate extends TypedHandlerDelegate {
    @Nonnull
    @Override
    public Result charTyped(char c, @Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
        if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
            return Result.CONTINUE;
        }

        if (c != '{') {
            return Result.CONTINUE;
        }
        if (!shouldProcess(file)) {
            return Result.CONTINUE;
        }

        int offset = editor.getCaretModel().getOffset();
        if (shouldCloseBrace(editor, offset, c)) {
            editor.getDocument().insertString(offset, "}");
            return Result.STOP;
        }
        return Result.CONTINUE;
    }

    private boolean shouldCloseBrace(Editor editor, int offset, char c) {
        CharSequence text = editor.getDocument().getCharsSequence();

        if (offset < 2) {
            return false;
        }
        if (c != '{' || text.charAt(offset - 2) != '$') {
            return false;
        }

        if (offset < text.length()) {
            char next = text.charAt(offset);
            if (next == '}') {
                return false;
            }
            if (Character.isLetterOrDigit(next)) {
                return false;
            }
        }

        return true;
    }

    public static boolean shouldProcess(PsiFile file) {
        return MavenDomUtil.isMavenFile(file) || MavenDomUtil.isFilteredResourceFile(file);
    }
}
