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
package org.jetbrains.idea.maven.dom.annotator;

import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.maven.rt.server.common.model.MavenProjectProblem;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomUtil;
import consulo.xml.util.xml.highlighting.DomElementAnnotationHolder;
import consulo.xml.util.xml.highlighting.DomElementsAnnotator;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.localize.MavenDomLocalize;
import org.jetbrains.idea.maven.project.MavenProject;

import javax.annotation.Nonnull;
import java.util.Arrays;

public class MavenDomAnnotator implements DomElementsAnnotator {
    @Override
    public void annotate(DomElement element, DomElementAnnotationHolder holder) {
        if (element instanceof MavenDomProjectModel domProjectModel) {
            addProblems(
                element,
                domProjectModel,
                holder,
                MavenProjectProblem.ProblemType.STRUCTURE,
                MavenProjectProblem.ProblemType.SETTINGS_OR_PROFILES
            );
        }
        else if (element instanceof MavenDomParent) {
            addProblems(
                element,
                DomUtil.getParentOfType(element, MavenDomProjectModel.class, true),
                holder,
                MavenProjectProblem.ProblemType.PARENT
            );
        }
    }

    private void addProblems(
        DomElement element,
        MavenDomProjectModel model,
        DomElementAnnotationHolder holder,
        MavenProjectProblem.ProblemType... types
    ) {
        MavenProject mavenProject = MavenDomUtil.findProject(model);
        if (mavenProject != null) {
            for (MavenProjectProblem each : mavenProject.getProblems()) {
                MavenProjectProblem.ProblemType type = each.getType();
                if (!Arrays.asList(types).contains(type)) {
                    continue;
                }
                VirtualFile problemFile = LocalFileSystem.getInstance().findFileByPath(each.getPath());

                LocalQuickFix[] fixes = LocalQuickFix.EMPTY_ARRAY;
                if (problemFile != null && !Comparing.equal(mavenProject.getFile(), problemFile)) {
                    fixes = new LocalQuickFix[]{new OpenProblemFileFix(problemFile)};
                }
                holder.createProblem(element, HighlightSeverity.ERROR, each.getDescription(), fixes);
            }
        }
    }

    private static class OpenProblemFileFix implements LocalQuickFix {
        private final VirtualFile myFile;

        private OpenProblemFileFix(VirtualFile file) {
            myFile = file;
        }

        @Nonnull
        @Override
        public String getName() {
            return MavenDomLocalize.fixOpenFile(myFile.getName()).get();
        }

        @Nonnull
        @Override
        public String getFamilyName() {
            return MavenDomLocalize.inspectionGroup().get();
        }

        @Override
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            OpenFileDescriptorFactory.getInstance(project).builder(myFile).build().navigate(true);
        }
    }
}
