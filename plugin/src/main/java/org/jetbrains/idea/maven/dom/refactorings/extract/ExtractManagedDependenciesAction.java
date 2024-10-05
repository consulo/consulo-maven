/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.refactorings.extract;

import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.Language;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.lang.Pair;
import consulo.xml.psi.xml.XmlElement;
import consulo.xml.util.xml.DomUtil;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomExclusion;
import org.jetbrains.idea.maven.dom.model.MavenDomExclusions;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class ExtractManagedDependenciesAction extends BaseRefactoringAction {
    public ExtractManagedDependenciesAction() {
        setInjectedContext(true);
    }

    protected boolean isAvailableInEditorOnly() {
        return true;
    }

    protected boolean isEnabledOnElements(@Nonnull PsiElement[] elements) {
        return false;
    }

    @Override
    protected boolean isAvailableForLanguage(Language language) {
        return true;
    }

    protected RefactoringActionHandler getHandler(@Nonnull DataContext dataContext) {
        return new MyRefactoringActionHandler();
    }

    @Override
    protected boolean isAvailableForFile(PsiFile file) {
        return MavenDomUtil.isMavenFile(file);
    }

    @Override
    protected boolean isAvailableOnElementInEditorAndFile(
        @Nonnull PsiElement element, @Nonnull Editor editor, @Nonnull PsiFile file,
        @Nonnull DataContext context
    ) {
        if (!super.isAvailableOnElementInEditorAndFile(element, editor, file, context)) {
            return false;
        }
        return findDependencyAndParent(file, editor) != null;
    }

    private static Pair<MavenDomDependency, Set<MavenDomProjectModel>> findDependencyAndParent(PsiFile file, Editor editor) {
        final MavenDomDependency dependency = DomUtil.findDomElement(
            file.findElementAt(editor.getCaretModel().getOffset()),
            MavenDomDependency.class
        );
        if (dependency == null || isManagedDependency(dependency)) {
            return null;
        }

        Set<MavenDomProjectModel> parents = getParentProjects(file);
        if (parents.isEmpty()) {
            return null;
        }

        return Pair.create(dependency, parents);
    }

    @Nonnull
    private static Set<MavenDomProjectModel> getParentProjects(@Nonnull PsiFile file) {
        final MavenDomProjectModel model = MavenDomUtil.getMavenDomModel(file, MavenDomProjectModel.class);

        if (model == null) {
            return Collections.emptySet();
        }
        return MavenDomProjectProcessorUtils.collectParentProjects(model);
    }

    private static boolean isManagedDependency(@Nonnull MavenDomDependency dependency) {
        return MavenDomProjectProcessorUtils.searchManagingDependency(dependency) != null;
    }

    private static class MyRefactoringActionHandler implements RefactoringActionHandler {
        public void invoke(@Nonnull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
            Pair<MavenDomDependency, Set<MavenDomProjectModel>> depAndParents = findDependencyAndParent(file, editor);
            if (depAndParents == null) {
                return;
            }

            final MavenDomDependency dependency = depAndParents.first;
            Set<MavenDomProjectModel> parent = depAndParents.second;

            Function<MavenDomProjectModel, Set<MavenDomDependency>> funOccurrences = getOccurencesFunction(dependency);
            final ProcessData processData =
                getProcessData(project, parent, funOccurrences, dependency.getExclusions().getXmlElement() != null);
            if (processData == null) {
                return;
            }

            final MavenDomProjectModel model = processData.getModel();
            final Set<MavenDomDependency> usages = processData.getUsages();
            final boolean extractExclusions = processData.isExtractExclusions();


            assert model != null;
            assert usages != null;

            new WriteCommandAction(project, getFiles(file, model, usages)) {
                @Override
                protected void run(Result result) throws Throwable {
                    MavenDomDependency addedDependency = model.getDependencyManagement().getDependencies().addDependency();
                    addedDependency.getGroupId().setStringValue(dependency.getGroupId().getStringValue());
                    addedDependency.getArtifactId().setStringValue(dependency.getArtifactId().getStringValue());
                    addedDependency.getVersion().setStringValue(dependency.getVersion().getStringValue());
                    String typeValue = dependency.getType().getStringValue();

                    dependency.getVersion().undefine();

                    if (typeValue != null) {
                        addedDependency.getType().setStringValue(typeValue);
                    }

                    String classifier = dependency.getClassifier().getStringValue();
                    if (classifier != null) {
                        addedDependency.getClassifier().setStringValue(classifier);
                    }

                    String systemPath = dependency.getSystemPath().getStringValue();
                    if (systemPath != null) {
                        addedDependency.getSystemPath().setStringValue(systemPath);
                        dependency.getSystemPath().undefine();
                    }


                    if (extractExclusions) {
                        MavenDomExclusions addedExclusions = addedDependency.getExclusions();
                        for (MavenDomExclusion exclusion : dependency.getExclusions().getExclusions()) {
                            MavenDomExclusion domExclusion = addedExclusions.addExclusion();

                            domExclusion.getGroupId().setStringValue(exclusion.getGroupId().getStringValue());
                            domExclusion.getArtifactId().setStringValue(exclusion.getArtifactId().getStringValue());
                        }

                        dependency.getExclusions().undefine();
                    }

                    for (MavenDomDependency usage : usages) {
                        usage.getVersion().undefine();
                    }
                }
            }.execute();
        }

        private static PsiFile[] getFiles(
            @Nonnull PsiFile file,
            @Nonnull MavenDomProjectModel model,
            @Nonnull Set<MavenDomDependency> usages
        ) {
            Set<PsiFile> files = new HashSet<>();

            files.add(file);
            XmlElement xmlElement = model.getXmlElement();
            if (xmlElement != null) {
                files.add(xmlElement.getContainingFile());
            }
            for (MavenDomDependency usage : usages) {
                XmlElement element = usage.getXmlElement();
                if (element != null) {
                    files.add(element.getContainingFile());
                }
            }

            return PsiUtilCore.toPsiFileArray(files);
        }


        @Nullable
        private static ProcessData getProcessData(
            @Nonnull Project project,
            @Nonnull Set<MavenDomProjectModel> models,
            @Nonnull Function<MavenDomProjectModel, Set<MavenDomDependency>> funOccurrences,
            boolean hasExclusions
        ) {
            if (models.size() == 0) {
                return null;
            }

            if (models.size() == 1 && !hasExclusions) {
                MavenDomProjectModel model = models.iterator().next();
                if (funOccurrences.apply(model).size() == 0) {
                    return new ProcessData(model, Collections.<MavenDomDependency>emptySet(), false);
                }
            }

            SelectMavenProjectDialog dialog = new SelectMavenProjectDialog(project, models, funOccurrences, hasExclusions);
            dialog.show();

            if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
                MavenDomProjectModel model = dialog.getSelectedProject();

                return new ProcessData(model, dialog.isReplaceAllOccurrences()
                    ? funOccurrences.apply(model)
                    : Collections.<MavenDomDependency>emptySet(), dialog.isExtractExclusions());
            }

            return null;
        }

        private static Function<MavenDomProjectModel, Set<MavenDomDependency>> getOccurencesFunction(final MavenDomDependency dependency) {
            return new Function<>() {
                public Set<MavenDomDependency> apply(MavenDomProjectModel model) {
                    DependencyConflictId dependencyId = DependencyConflictId.create(dependency);
                    if (dependencyId == null) {
                        return Collections.emptySet();
                    }

                    return MavenDomProjectProcessorUtils.searchDependencyUsages(model, dependencyId, Collections.singleton(dependency));
                }
            };
        }

        public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        }
    }

    private static class ProcessData {
        private final MavenDomProjectModel myModel;
        private final Set<MavenDomDependency> myUsages;
        private final boolean myExtractExclusions;

        public MavenDomProjectModel getModel() {
            return myModel;
        }

        public Set<MavenDomDependency> getUsages() {
            return myUsages;
        }

        public boolean isExtractExclusions() {
            return myExtractExclusions;
        }

        public ProcessData(MavenDomProjectModel model, Set<MavenDomDependency> usages, boolean extractExclusions) {
            myModel = model;
            myUsages = usages;
            myExtractExclusions = extractExclusions;
        }
    }
}
