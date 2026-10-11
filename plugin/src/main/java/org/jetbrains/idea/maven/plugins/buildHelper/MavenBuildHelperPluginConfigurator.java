// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Copyright 2013-2026 consulo.io
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
package org.jetbrains.idea.maven.plugins.buildHelper;

import consulo.annotation.component.ExtensionImpl;
import consulo.content.ContentFolderTypeProvider;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.ProductionResourceContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.content.TestResourceContentFolderTypeProvider;
import consulo.maven.importing.MavenImporterFromBuildPlugin;
import consulo.maven.rt.server.common.model.MavenPlugin;
import consulo.module.Module;
import org.jetbrains.idea.maven.importing.BuildHelperMavenPluginUtil;
import org.jetbrains.idea.maven.importing.MavenContentFolder;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@ExtensionImpl
public class MavenBuildHelperPluginConfigurator extends MavenImporterFromBuildPlugin {
    public MavenBuildHelperPluginConfigurator() {
        super("org.codehaus.mojo", "build-helper-maven-plugin");
    }

    @Override
    public void preProcess(Module module,
                           MavenProject mavenProject,
                           MavenProjectChanges changes,
                           MavenModifiableModelsProvider modifiableModelsProvider) {
    }

    @Override
    public void process(MavenModifiableModelsProvider modifiableModelsProvider,
                        Module module,
                        MavenRootModelAdapter rootModel,
                        MavenProjectsTree mavenModel,
                        MavenProject mavenProject,
                        MavenProjectChanges changes,
                        Map<MavenProject, String> mavenProjectToModuleName,
                        List<MavenProjectsProcessorTask> postTasks) {
    }

    @Override
    public void collectContentFolders(MavenProject mavenProject,
                                      BiFunction<ContentFolderTypeProvider, String, MavenContentFolder> folderAcceptor) {
        MavenPlugin buildHelperPlugin = BuildHelperMavenPluginUtil.findPlugin(mavenProject);

        if (buildHelperPlugin == null) {
            return;
        }
        BuildHelperMavenPluginUtil.addBuilderHelperPaths(buildHelperPlugin, "add-source", path -> {
            folderAcceptor.apply(ProductionContentFolderTypeProvider.getInstance(), path);
        });
        BuildHelperMavenPluginUtil.addBuilderHelperResourcesPaths(buildHelperPlugin, "add-resource", path -> {
            folderAcceptor.apply(ProductionResourceContentFolderTypeProvider.getInstance(), path);
        });

        BuildHelperMavenPluginUtil.addBuilderHelperPaths(buildHelperPlugin, "add-test-source", path -> {
            folderAcceptor.apply(TestContentFolderTypeProvider.getInstance(), path);
        });
        BuildHelperMavenPluginUtil.addBuilderHelperResourcesPaths(buildHelperPlugin, "add-test-resource", path -> {
            folderAcceptor.apply(TestResourceContentFolderTypeProvider.getInstance(), path);
        });
    }
}
