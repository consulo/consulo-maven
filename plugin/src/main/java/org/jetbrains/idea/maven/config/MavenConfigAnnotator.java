// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.config;

import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.language.psi.PsiElement;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class MavenConfigAnnotator implements Annotator {
    @Override
    public void annotate(PsiElement element, AnnotationHolder holder) {
        if (element instanceof PsiPlainTextFile file) {
            VirtualFile elementFile = file.getContainingFile().getVirtualFile();
            if (!isConfigFile(elementFile)) {
                return;
            }

            MavenProjectsManager manager = MavenProjectsManager.getInstance(element.getProject());
            if (!manager.isMavenizedProject()) {
                return;
            }

            MavenProject mavenProject = manager.getRootProjects().stream()
                .filter(p -> elementFile.getParent() != null && elementFile.getParent().getParent() != null &&
                    p.getDirectoryFile().equals(elementFile.getParent().getParent()))
                .findFirst().orElse(null);
            if (mavenProject == null) return;

            String error = mavenProject.getConfigFileError();
            if (error == null) return;

            holder.newAnnotation(HighlightSeverity.ERROR, error).create();
        }
    }

    private boolean isConfigFile(VirtualFile file) {
        VirtualFile parent = file != null ? file.getParent() : null;
        return file != null && "maven.config".equals(file.getName()) &&
            parent != null && ".mvn".equals(parent.getName());
    }
}
