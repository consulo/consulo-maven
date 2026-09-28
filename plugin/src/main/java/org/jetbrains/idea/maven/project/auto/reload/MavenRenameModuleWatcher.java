// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload;

import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.language.editor.WriteCommandAction;
import consulo.language.psi.PsiDocumentManager;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.event.ModuleListener;
import consulo.project.Project;
import consulo.xml.dom.DomUtil;
import consulo.xml.language.psi.XmlFile;
import consulo.xml.language.psi.XmlTag;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependencies;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomExclusion;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Map;
import java.util.function.Consumer;

public class MavenRenameModuleWatcher implements ModuleListener {
    @Override
    public void modulesRenamed(Project project, Map<Module, String> modulesWithOldName) {
        for (Map.Entry<Module, String> entry : modulesWithOldName.entrySet()) {
            new MavenRenameModuleHandler(project, entry.getKey(), entry.getValue()).handleModuleRename();
        }
    }

    private static class MavenRenameModuleHandler {
        private final Project myProject;
        private final Module myModule;
        private final String myOldName;
        private final String myNewName;
        private String myGroupId;
        private final MavenProjectsManager myProjectsManager;

        private MavenRenameModuleHandler(Project project, Module module, String oldName) {
            myProject = project;
            myModule = module;
            myOldName = oldName;

            // handle module groups: group.subgroup.module
            String[] myNewNameHierarchy = module.getName().split("\\.");
            myNewName = myNewNameHierarchy[myNewNameHierarchy.length - 1];

            myProjectsManager = MavenProjectsManager.getInstance(project);
        }

        private void replaceArtifactId(@Nullable XmlTag parentTag) {
            if (null == parentTag) {
                return;
            }
            XmlTag artifactIdTag = parentTag.findFirstSubTag("artifactId");
            if (null == artifactIdTag) {
                return;
            }
            XmlTag groupIdTag = parentTag.findFirstSubTag("groupId");
            if (null == groupIdTag) {
                return;
            }
            if (myGroupId.equals(groupIdTag.getValue().getText()) && myOldName.equals(artifactIdTag.getValue().getText())) {
                artifactIdTag.getValue().setText(myNewName);
            }
        }

        private void replaceModuleArtifactId(MavenDomProjectModel mavenModel) {
            XmlTag artifactIdTag = mavenModel.getArtifactId().getXmlTag();
            if (null != artifactIdTag) {
                if (myOldName.equals(artifactIdTag.getValue().getText())) {
                    artifactIdTag.getValue().setText(myNewName);
                }
            }
        }

        private void replaceArtifactIdReferences(MavenDomDependencies dependencies) {
            for (MavenDomDependency dependency : dependencies.getDependencies()) {
                replaceArtifactId(dependency.getXmlTag());
                for (MavenDomExclusion exclusion : dependency.getExclusions().getExclusions()) {
                    replaceArtifactId(exclusion.getXmlTag());
                }
            }
        }

        private void replaceArtifactIdReferences(MavenDomProjectModel mavenModel) {
            if (null != mavenModel.getXmlTag()) {
                // parent artifactId
                replaceArtifactId(mavenModel.getXmlTag().findFirstSubTag("parent"));
            }

            // dependencies and exclusions
            replaceArtifactIdReferences(mavenModel.getDependencies());

            // dependency management
            replaceArtifactIdReferences(mavenModel.getDependencyManagement().getDependencies());
        }

        private void processModule(Module module, Consumer<MavenDomProjectModel> artifactIdReplacer) {
            if (!myProjectsManager.isMavenizedModule(module)) {
                return;
            }
            MavenProject mavenProject = myProjectsManager.findProject(module);
            if (null == mavenProject) {
                return;
            }
            MavenDomProjectModel mavenModel = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProject.getFile());
            if (null == mavenModel) {
                return;
            }
            XmlFile psiFile = DomUtil.getFile(mavenModel);

            WriteCommandAction.writeCommandAction(psiFile).run(() -> {
                PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
                Document document = documentManager.getDocument(psiFile);
                if (document != null) {
                    documentManager.commitDocument(document);
                }

                artifactIdReplacer.accept(mavenModel);

                if (document != null) {
                    FileDocumentManager.getInstance().saveDocument(document);
                }
            });
        }

        public void handleModuleRename() {
            if (!myProjectsManager.isMavenizedModule(myModule)) {
                return;
            }
            MavenProject mavenProject = myProjectsManager.findProject(myModule);
            if (null == mavenProject) {
                return;
            }
            myGroupId = mavenProject.getMavenId().getGroupId();

            Module[] modules = ModuleManager.getInstance(myProject).getModules();
            for (Module module : modules) {
                if (module == myModule) {
                    processModule(module, this::replaceModuleArtifactId);
                }
                else {
                    processModule(module, this::replaceArtifactIdReferences);
                }
            }
        }
    }
}
