package org.jetbrains.idea.maven.utils;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.ProblemHighlightFilter;
import consulo.language.psi.PsiFile;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl
public class ArchetypeResourceHighlightFilter extends ProblemHighlightFilter {
    @Override
    public boolean shouldHighlight(@Nonnull PsiFile psiFile) {
        VirtualFile virtualFile = psiFile.getOriginalFile().getVirtualFile();

        do {
            if (virtualFile == null) {
                return true;
            }

            if (virtualFile.getName().equals("archetype-resources")) {
                if (virtualFile.getPath().endsWith("src/main/resources/archetype-resources")) {
                    return false;
                }
            }

            virtualFile = virtualFile.getParent();
        }
        while (true);
    }
}
