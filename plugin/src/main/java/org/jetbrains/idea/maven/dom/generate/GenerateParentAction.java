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

import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.language.editor.WriteCommandAction;
import consulo.maven.icon.MavenIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.xml.util.xml.DomUtil;
import consulo.xml.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.localize.MavenDomLocalize;
import org.jetbrains.idea.maven.navigator.SelectMavenProjectDialog;
import org.jetbrains.idea.maven.project.MavenProject;

import javax.annotation.Nonnull;

public class GenerateParentAction extends GenerateDomElementAction {
    public GenerateParentAction() {
        super(
            new MavenGenerateProvider<>(MavenDomLocalize.generateParent().get(), MavenDomParent.class) {
                @Override
                @RequiredUIAccess
                protected MavenDomParent doGenerate(@Nonnull final MavenDomProjectModel mavenModel, Editor editor) {
                    SelectMavenProjectDialog d = new SelectMavenProjectDialog(editor.getProject(), null);
                    d.show();
                    if (!d.isOK()) {
                        return null;
                    }
                    final MavenProject parentProject = d.getResult();
                    if (parentProject == null) {
                        return null;
                    }

                    return new WriteCommandAction<MavenDomParent>(editor.getProject(), getDescription()) {
                        @Override
                        @RequiredWriteAction
                        protected void run(Result<MavenDomParent> result) throws Throwable {
                            result.setResult(MavenDomUtil.updateMavenParent(mavenModel, parentProject));
                        }
                    }.execute().getResultObject();
                }

                @Override
                protected boolean isAvailableForModel(MavenDomProjectModel mavenModel) {
                    return !DomUtil.hasXml(mavenModel.getMavenParent());
                }
            },
            MavenIconGroup.mavenlogo()
        );
    }

    @Override
    protected boolean startInWriteAction() {
        return false;
    }
}
