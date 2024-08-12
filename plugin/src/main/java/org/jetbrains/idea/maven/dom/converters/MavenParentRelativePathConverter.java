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
package org.jetbrains.idea.maven.dom.converters;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.util.xml.*;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.references.MavenPathReferenceConverter;
import org.jetbrains.idea.maven.localize.MavenDomLocalize;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MavenParentRelativePathConverter extends ResolvingConverter<PsiFile> implements CustomReferenceConverter {
    @Override
    @RequiredReadAction
    public PsiFile fromString(@Nullable String s, ConvertContext context) {
        if (StringUtil.isEmptyOrSpaces(s)) {
            return null;
        }

        VirtualFile contextFile = context.getFile().getVirtualFile();
        if (contextFile == null) {
            return null;
        }

        VirtualFile parent = contextFile.getParent();
        VirtualFile f = parent == null ? null : parent.findFileByRelativePath(s);
        if (f == null) {
            return null;
        }

        if (f.isDirectory()) {
            f = f.findChild(MavenConstants.POM_XML);
        }
        if (f == null) {
            return null;
        }

        return context.getPsiManager().findFile(f);
    }

    @Override
    public String toString(@Nullable PsiFile f, ConvertContext context) {
        if (f == null) {
            return null;
        }
        VirtualFile currentFile = context.getFile().getOriginalFile().getVirtualFile();
        if (currentFile == null) {
            return null;
        }

        return MavenDomUtil.calcRelativePath(currentFile.getParent(), f.getVirtualFile());
    }

    @Nonnull
    @Override
    public Collection<PsiFile> getVariants(ConvertContext context) {
        List<PsiFile> result = new ArrayList<>();
        PsiFile currentFile = context.getFile().getOriginalFile();
        for (DomFileElement<MavenDomProjectModel> each : MavenDomUtil.collectProjectModels(context.getFile().getProject())) {
            PsiFile file = each.getOriginalFile();
            if (file == currentFile) {
                continue;
            }
            result.add(file);
        }
        return result;
    }

    @Override
    public LocalQuickFix[] getQuickFixes(ConvertContext context) {
        return ArrayUtil.append(super.getQuickFixes(context), new RelativePathFix(context));
    }

    private static class RelativePathFix implements LocalQuickFix {
        private final ConvertContext myContext;

        public RelativePathFix(ConvertContext context) {
            myContext = context;
        }

        @Nonnull
        @Override
        public String getName() {
            return MavenDomLocalize.fixParentPath().get();
        }

        @Nonnull
        @Override
        public String getFamilyName() {
            return MavenDomLocalize.inspectionGroup().get();
        }

        @Override
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            GenericDomValue el = (GenericDomValue)myContext.getInvocationElement();
            MavenId id = MavenArtifactCoordinatesHelper.getId(myContext);

            MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
            MavenProject parentFile = manager.findProject(id);
            if (parentFile != null) {
                VirtualFile currentFile = myContext.getFile().getVirtualFile();
                el.setStringValue(MavenDomUtil.calcRelativePath(currentFile.getParent(), parentFile.getFile()));
            }
        }
    }

    @Nonnull
    @Override
    public PsiReference[] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
        return new MavenPathReferenceConverter(item -> item.isDirectory() || item.getName().equals("pom.xml"))
            .createReferences(genericDomValue, element, context);
    }
}