/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import consulo.application.WriteAction;
import consulo.content.ContentFolderTypeProvider;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.ProductionResourceContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.content.TestResourceContentFolderTypeProvider;
import consulo.maven.rt.server.common.model.MavenResource;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModifiableModelCommitter;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.Path;

import java.io.File;
import java.util.*;

public class MavenFoldersImporter {
    private record PathWithGenerated(Path path, ContentFolderTypeProvider type, boolean generated) {
    }

    private final MavenProject myMavenProject;
    private final MavenImportingSettings myImportingSettings;
    private final MavenRootModelAdapter myModel;

    public static void updateProjectFolders(final Project project, final boolean updateTargetFoldersOnly) {
        final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
        final MavenImportingSettings settings = manager.getImportingSettings();

        WriteAction.runAndWait(() ->
        {
            List<ModifiableRootModel> rootModels = new ArrayList<>();
            for (Module each : ModuleManager.getInstance(project).getModules()) {
                MavenProject mavenProject = manager.findProject(each);
                if (mavenProject == null) {
                    continue;
                }

                MavenRootModelAdapter a = new MavenRootModelAdapter(mavenProject, each, new MavenDefaultModifiableModelsProvider(project));
                new MavenFoldersImporter(mavenProject, settings, a).config(updateTargetFoldersOnly);

                ModifiableRootModel model = a.getRootModel();
                if (model.isChanged()) {
                    rootModels.add(model);
                }
                else {
                    model.dispose();
                }
            }

            if (!rootModels.isEmpty()) {
                ModifiableRootModel[] modelsArray = rootModels.toArray(new ModifiableRootModel[rootModels.size()]);
                if (modelsArray.length > 0) {
                    ModifiableModelCommitter.getInstance(project).multiCommit(modelsArray, ModuleManager.getInstance(project).getModifiableModel());
                }
            }
        });
    }

    public MavenFoldersImporter(MavenProject mavenProject, MavenImportingSettings settings, MavenRootModelAdapter model) {
        myMavenProject = mavenProject;
        myImportingSettings = settings;
        myModel = model;
    }

    public void config() {
        config(false);
    }

    private void config(boolean updateTargetFoldersOnly) {
        List<MavenImporter> importers = MavenImporter.getSuitableImporters(myMavenProject);

        if (!updateTargetFoldersOnly) {
            if (!myImportingSettings.isKeepSourceFolders()) {
                myModel.clearSourceFolders();
            }

            configSourceFolders(importers);

            configOutputFolders();
        }

        configGeneratedAndExcludedFolders(importers);
    }

    private void configSourceFolders(@Nonnull List<MavenImporter> importers) {
        MultiMap<ContentFolderTypeProvider, String> contentFolders = new MultiMap<>();

        contentFolders.putValues(ProductionContentFolderTypeProvider.getInstance(), myMavenProject.getSources());
        contentFolders.putValues(TestContentFolderTypeProvider.getInstance(), myMavenProject.getTestSources());

        Set<String> generatedPaths = new HashSet<>();

        for (MavenImporter each : importers) {
            each.collectContentFolders(myMavenProject, (contentFolderTypeProvider, sourceFolderPath) -> {
                contentFolders.putValue(contentFolderTypeProvider, sourceFolderPath);

                return new MavenContentFolder() {
                    @Nonnull
                    @Override
                    public MavenContentFolder setGenerated() {
                        generatedPaths.add(sourceFolderPath);
                        return this;
                    }
                };
            });
        }

        for (MavenResource each : myMavenProject.getResources()) {
            String directory = each.getDirectory();
            // do not allow override it
            if (contentFolders.containsScalarValue(directory)) {
                continue;
            }
            contentFolders.putValue(ProductionResourceContentFolderTypeProvider.getInstance(), directory);
        }

        for (MavenResource each : myMavenProject.getTestResources()) {
            String directory = each.getDirectory();
            // do not allow override it
            if (contentFolders.containsScalarValue(directory)) {
                continue;
            }
            contentFolders.putValue(TestResourceContentFolderTypeProvider.getInstance(), directory);
        }

        addBuilderHelperPaths("add-source", ProductionContentFolderTypeProvider.getInstance(), contentFolders);
        addBuilderHelperPaths("add-test-source", TestContentFolderTypeProvider.getInstance(), contentFolders);

        MultiMap<ContentFolderTypeProvider, PathWithGenerated> allFolders = new MultiMap<>();

        for (Map.Entry<ContentFolderTypeProvider, Collection<String>> entry : contentFolders.entrySet()) {
            ContentFolderTypeProvider type = entry.getKey();

            for (String path : entry.getValue()) {
                allFolders.putValue(type, new PathWithGenerated(myModel.toPath(path), type, generatedPaths.contains(path)));
            }
        }

        for (PathWithGenerated each : normalize(allFolders)) {
            myModel.addSourceFolder(each.path().getPath(), each.type(), each.generated());
        }
    }

    private void addBuilderHelperPaths(String goal, ContentFolderTypeProvider typeProvider, MultiMap<ContentFolderTypeProvider, String> folders) {
        Element configurationElement = myMavenProject.getPluginGoalConfiguration("org.codehaus.mojo", "build-helper-maven-plugin", goal);
        if (configurationElement != null) {
            Element sourcesElement = configurationElement.getChild("sources");
            if (sourcesElement != null) {
                for (Element element : sourcesElement.getChildren()) {
                    folders.putValue(typeProvider, element.getTextTrim());
                }
            }
        }
    }

    @Nonnull
    private static List<PathWithGenerated> normalize(@Nonnull MultiMap<ContentFolderTypeProvider, PathWithGenerated> folders) {
        List<PathWithGenerated> result = new ArrayList<>(folders.size());
        for (Map.Entry<ContentFolderTypeProvider, Collection<PathWithGenerated>> entry : folders.entrySet()) {
            for (PathWithGenerated pathWithGenerated : entry.getValue()) {
                addSourceFolder(pathWithGenerated.path(), pathWithGenerated.type(), pathWithGenerated.generated(), result);
            }
        }
        return result;
    }

    private static void addSourceFolder(Path path,
                                        ContentFolderTypeProvider provider,
                                        boolean generatedFolder,
                                        List<PathWithGenerated> result) {
        for (PathWithGenerated eachExisting : result) {
            if (MavenRootModelAdapter.isEqualOrAncestor(eachExisting.path().getPath(), path.getPath())
                || MavenRootModelAdapter.isEqualOrAncestor(path.getPath(), eachExisting.path().getPath())) {
                return;
            }
        }

        result.add(new PathWithGenerated(path, provider, generatedFolder));
    }

    private void configOutputFolders() {
        if (myImportingSettings.isUseMavenOutput()) {
            myModel.useModuleOutput(myMavenProject.getOutputDirectory(), myMavenProject.getTestOutputDirectory());
        }
        myModel.addExcludedFolder(myMavenProject.getOutputDirectory());
        myModel.addExcludedFolder(myMavenProject.getTestOutputDirectory());
    }

    private void configGeneratedAndExcludedFolders(@Nonnull List<MavenImporter> importers) {
        File targetDir = new File(myMavenProject.getBuildDirectory());

        String generatedDir = myMavenProject.getGeneratedSourcesDirectory(false);
        String generatedDirTest = myMavenProject.getGeneratedSourcesDirectory(true);

        myModel.unregisterAll(targetDir.getPath(), true, false);

        if (myImportingSettings.getGeneratedSourcesFolder() != MavenImportingSettings.GeneratedSourcesFolder.IGNORE) {
            myModel.addSourceFolder(myMavenProject.getAnnotationProcessorDirectory(true), TestContentFolderTypeProvider.getInstance(), true, true);
            myModel.addSourceFolder(myMavenProject.getAnnotationProcessorDirectory(false), ProductionContentFolderTypeProvider.getInstance(), true,
                true);
        }

        File[] targetChildren = targetDir.listFiles();

        if (targetChildren != null) {
            for (File f : targetChildren) {
                if (!f.isDirectory()) {
                    continue;
                }

                if (FileUtil.pathsEqual(generatedDir, f.getPath())) {
                    configGeneratedSourceFolder(importers, f, ProductionContentFolderTypeProvider.getInstance());
                }
                else if (FileUtil.pathsEqual(generatedDirTest, f.getPath())) {
                    configGeneratedSourceFolder(importers, f, TestContentFolderTypeProvider.getInstance());
                }
                else {
                    if (myImportingSettings.isExcludeTargetFolder()) {
                        if (myModel.isAlreadyExcluded(f)) {
                            continue;
                        }
                        myModel.addExcludedFolder(f.getPath());
                    }
                }
            }
        }

        List<String> excludedDirectories = new ArrayList<>();
        for (MavenImporter each : importers) {
            each.collectExcludedFolders(myMavenProject, excludedDirectories::add);
        }

        for (String eachFolder : excludedDirectories) {
            myModel.unregisterAll(eachFolder, true, true);
            myModel.addExcludedFolder(eachFolder);
        }

        if (myImportingSettings.isExcludeTargetFolder()) {
            myModel.addExcludedFolder(targetDir.getPath());
        }
        else {
            myModel.addExcludedFolder(myMavenProject.getOutputDirectory());
            myModel.addExcludedFolder(myMavenProject.getTestOutputDirectory());
        }
    }

    private void configGeneratedSourceFolder(@Nonnull List<MavenImporter> importers,
                                             @Nonnull File targetDir,
                                             @Nonnull ContentFolderTypeProvider typeProvider) {
        switch (myImportingSettings.getGeneratedSourcesFolder()) {
            case GENERATED_SOURCE_FOLDER:
                if (!isExcludeGenerationSourceFolder(importers, targetDir, typeProvider)) {
                    myModel.addSourceFolder(targetDir.getPath(), typeProvider, true, true);
                }
                break;
            case SUBFOLDER:
                addAllSubDirsAsSources(importers, targetDir, typeProvider, true);
                break;
            case IGNORE:
                break; // Ignore.
        }
    }

    private void addAllSubDirsAsSources(@Nonnull List<MavenImporter> importers,
                                        @Nonnull File dir,
                                        @Nonnull ContentFolderTypeProvider typeProvider,
                                        boolean generated) {
        for (File f : getChildren(dir)) {
            if (!f.isDirectory()) {
                continue;
            }

            if (isExcludeGenerationSourceFolder(importers, f, typeProvider)) {
                continue;
            }

            myModel.addSourceFolder(dir.getPath(), typeProvider, true, generated);
        }
    }


    private boolean isExcludeGenerationSourceFolder(@Nonnull List<MavenImporter> importers,
                                                    @Nonnull File targetDir,
                                                    @Nonnull ContentFolderTypeProvider typeProvider) {
        for (MavenImporter importer : importers) {
            if (importer.isExcludedGenerationSourceFolder(myMavenProject, targetDir.getPath(), typeProvider)) {
                return true;
            }
        }

        return false;
    }

    private static File[] getChildren(File dir) {
        File[] result = dir.listFiles();
        return result == null ? ArrayUtil.EMPTY_FILE_ARRAY : result;
    }
}
