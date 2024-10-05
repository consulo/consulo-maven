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
package org.jetbrains.idea.maven.dom.generate;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.AllIcons;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.language.editor.WriteCommandAction;
import consulo.language.psi.PsiDocumentManager;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.xml.psi.xml.XmlElement;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.util.xml.DomUtil;
import consulo.xml.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomDependencyManagement;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.idea.maven.localize.MavenDomLocalize;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class GenerateDependencyAction extends GenerateDomElementAction {
    public GenerateDependencyAction() {
        super(new MavenGenerateProvider<>(MavenDomLocalize.generateDependency().get(), MavenDomDependency.class) {
            @Nullable
            @Override
            protected MavenDomDependency doGenerate(@Nonnull final MavenDomProjectModel mavenModel, final Editor editor) {
                Project project = mavenModel.getManager().getProject();

                final Map<DependencyConflictId, MavenDomDependency> managedDependencies = GenerateManagedDependencyAction
                    .collectManagingDependencies(mavenModel);

                final List<MavenId> ids = MavenArtifactSearchDialog.searchForArtifact(project, managedDependencies.values());
                if (ids.isEmpty()) {
                    return null;
                }

                PsiDocumentManager.getInstance(project).commitAllDocuments();

                XmlFile psiFile = DomUtil.getFile(mavenModel);
                return new WriteCommandAction<MavenDomDependency>(psiFile.getProject(), "Generate Dependency", psiFile) {
                    @Override
                    @RequiredReadAction
                    protected void run(Result<MavenDomDependency> result) throws Throwable {
                        MavenDomDependencyManagement dependencyManagement = mavenModel.getDependencyManagement();
                        XmlElement managedDependencyXml = dependencyManagement.getXmlElement();
                        boolean isInsideManagedDependencies = managedDependencyXml != null
                            && managedDependencyXml.getTextRange().contains(editor.getCaretModel().getOffset());

                        for (MavenId each : ids) {
                            MavenDomDependency res;
                            if (isInsideManagedDependencies) {
                                res = MavenDomUtil.createDomDependency(dependencyManagement.getDependencies(), editor, each);
                            }
                            else {
                                DependencyConflictId conflictId =
                                    new DependencyConflictId(each.getGroupId(), each.getArtifactId(), null, null);
                                MavenDomDependency managedDependenciesDom = managedDependencies.get(conflictId);

                                if (managedDependenciesDom != null
                                    && Comparing.equal(each.getVersion(), managedDependenciesDom.getVersion().getStringValue())) {
                                    // Generate dependency without <version> tag
                                    res = MavenDomUtil.createDomDependency(mavenModel.getDependencies(), editor);

                                    res.getGroupId().setStringValue(conflictId.getGroupId());
                                    res.getArtifactId().setStringValue(conflictId.getArtifactId());
                                }
                                else {
                                    res = MavenDomUtil.createDomDependency(mavenModel.getDependencies(), editor, each);
                                }
                            }
                            result.setResult(res);
                        }
                    }
                }.execute().getResultObject();
            }
        }, AllIcons.Nodes.PpLib);
    }

    @Override
    protected boolean startInWriteAction() {
        return false;
    }
}
