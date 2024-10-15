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

import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.util.PathUtil;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.LocalQuickFixProvider;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.xml.util.xml.DomFileElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.localize.MavenDomLocalize;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MavenModulePsiReference extends MavenPsiReference implements LocalQuickFixProvider {
    public MavenModulePsiReference(PsiElement element, String text, TextRange range) {
        super(element, text, range);
    }

    @Override
    @RequiredReadAction
    public PsiElement resolve() {
        VirtualFile baseDir = myPsiFile.getVirtualFile().getParent();
        if (baseDir == null) {
            return null;
        }
        String relPath = FileUtil.toSystemIndependentName(myText + "/" + MavenConstants.POM_XML);
        VirtualFile file = baseDir.findFileByRelativePath(relPath);

        if (file == null) {
            return null;
        }

        return getPsiFile(file);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public Object[] getVariants() {
        List<DomFileElement<MavenDomProjectModel>> files = MavenDomUtil.collectProjectModels(getProject());

        List<Object> result = new ArrayList<>();

        for (DomFileElement<MavenDomProjectModel> eachDomFile : files) {
            VirtualFile eachVFile = eachDomFile.getOriginalFile().getVirtualFile();
            if (Comparing.equal(eachVFile, myVirtualFile)) {
                continue;
            }

            PsiFile psiFile = eachDomFile.getFile();
            String modulePath = calcRelativeModulePath(myVirtualFile, eachVFile);

            result.add(LookupElementBuilder.create(psiFile, modulePath).withPresentableText(modulePath));
        }

        return result.toArray();
    }

    public static String calcRelativeModulePath(VirtualFile parentPom, VirtualFile modulePom) {
        String result = MavenDomUtil.calcRelativePath(parentPom.getParent(), modulePom);
        int to = result.length() - ("/" + MavenConstants.POM_XML).length();
        if (to < 0) {
            // todo IDEADEV-35440
            throw new RuntimeException("Filed to calculate relative path for:" +
                "\nparentPom: " + parentPom + "(valid: " + parentPom.isValid() + ")" +
                "\nmodulePom: " + modulePom + "(valid: " + modulePom.isValid() + ")" +
                "\nequals:" + parentPom.equals(modulePom));
        }
        return result.substring(0, to);
    }

    @RequiredReadAction
    private PsiFile getPsiFile(VirtualFile file) {
        return PsiManager.getInstance(getProject()).findFile(file);
    }

    private Project getProject() {
        return myPsiFile.getProject();
    }

    @Override
    public LocalQuickFix[] getQuickFixes() {
        if (myText.length() == 0 || resolve() != null) {
            return LocalQuickFix.EMPTY_ARRAY;
        }
        return new LocalQuickFix[]{new CreateModuleFix(true), new CreateModuleFix(false)};
    }

    private class CreateModuleFix implements LocalQuickFix {
        private final boolean myWithParent;

        private CreateModuleFix(boolean withParent) {
            myWithParent = withParent;
        }

        @Nonnull
        @Override
        public String getName() {
            return myWithParent
                ? MavenDomLocalize.fixCreateModuleWithParent().get()
                : MavenDomLocalize.fixCreateModule().get();
        }

        @Nonnull
        @Override
        public String getFamilyName() {
            return MavenDomLocalize.inspectionGroup().get();
        }

        @Override
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor d) {
            try {
                VirtualFile modulePom = createModulePom();
                MavenId id = MavenDomUtil.describe(myPsiFile);

                String groupId = id.getGroupId() == null ? "groupId" : id.getGroupId();
                String artifactId = modulePom.getParent().getName();
                String version = id.getVersion() == null ? "version" : id.getVersion();
                MavenUtil.runOrApplyMavenProjectFileTemplate(
                    project,
                    modulePom,
                    new MavenId(groupId, artifactId, version),
                    myWithParent ? id : null,
                    myPsiFile.getVirtualFile(),
                    true
                );
            }
            catch (IOException e) {
                MavenUtil.showError(project, "Cannot create a module", e);
            }
        }

        private VirtualFile createModulePom() throws IOException {
            VirtualFile baseDir = myVirtualFile.getParent();
            String modulePath = PathUtil.getCanonicalPath(baseDir.getPath() + "/" + myText);
            VirtualFile moduleDir = VirtualFileUtil.createDirectories(modulePath);
            return moduleDir.createChildData(this, MavenConstants.POM_XML);
        }
    }
}
