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
package org.jetbrains.idea.maven.importing;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.content.ContentFolderTypeProvider;
import consulo.maven.rt.server.common.model.MavenArtifact;
import consulo.maven.rt.server.common.server.NativeMavenProjectHolder;
import consulo.module.Module;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.project.Project;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class MavenImporter {
    public static List<MavenImporter> getSuitableImporters(MavenProject p) {
        return Application.get().getExtensionPoint(MavenImporter.class).collectFiltered(importer -> importer.isApplicable(p));
    }

    public abstract boolean isApplicable(MavenProject mavenProject);

    @Nullable
    protected String getId() {
        return null;
    }

    public void getSupportedPackagings(Collection<String> result) {
    }

    public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type) {
    }

    public void getSupportedDependencyScopes(Collection<String> result) {
    }

    @Nullable
    public Pair<String, String> getExtraArtifactClassifierAndExtension(MavenArtifact artifact, MavenExtraArtifactType type) {
        return null;
    }

    public void resolve(Project project,
                        MavenProject mavenProject,
                        NativeMavenProjectHolder nativeMavenProject,
                        MavenEmbedderWrapper embedder,
                        ResolveContext resolveContext) throws MavenProcessCanceledException {
    }

    public abstract void preProcess(Module module, MavenProject mavenProject, MavenProjectChanges changes, MavenModifiableModelsProvider modifiableModelsProvider);

    public abstract void process(MavenModifiableModelsProvider modifiableModelsProvider,
                                 Module module,
                                 MavenRootModelAdapter rootModel,
                                 MavenProjectsTree mavenModel,
                                 MavenProject mavenProject,
                                 MavenProjectChanges changes,
                                 Map<MavenProject, String> mavenProjectToModuleName,
                                 List<MavenProjectsProcessorTask> postTasks);

    @SuppressWarnings("unchecked")
    public <T extends ModuleExtension<T>> T enableModuleExtension(Module module, MavenModifiableModelsProvider modelsProvider, Class<T> clazz) {
        final ModifiableRootModel rootModel = modelsProvider.getRootModel(module);

        final MutableModuleExtension<T> extensionWithoutCheck = (MutableModuleExtension<T>) rootModel.getExtensionWithoutCheck(clazz);

        extensionWithoutCheck.setEnabled(true);

        return (T) extensionWithoutCheck;
    }

    public boolean processChangedModulesOnly() {
        return true;
    }

    public void collectContentFolders(MavenProject mavenProject, BiFunction<ContentFolderTypeProvider, String, MavenContentFolder> folderAcceptor) {
    }

    public void collectExcludedFolders(@Nonnull MavenProject mavenProject, @Nonnull Consumer<String> pathAcceptor) {
    }

    public boolean isExcludedGenerationSourceFolder(@Nonnull MavenProject mavenProject,
                                                    @Nonnull String sourcePath,
                                                    @Nonnull ContentFolderTypeProvider typeProvider) {
        return false;
    }
}
