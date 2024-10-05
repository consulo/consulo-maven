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
package org.jetbrains.idea.maven.dom;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.function.Processor;
import consulo.language.psi.PsiElement;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.util.xml.DomUtil;
import consulo.xml.util.xml.GenericDomValue;
import consulo.xml.util.xml.impl.GenericDomValueReference;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class MavenDomProjectProcessorUtils {
    private MavenDomProjectProcessorUtils() {
    }

    @Nonnull
    @RequiredReadAction
    public static Set<MavenDomProjectModel> getChildrenProjects(@Nonnull final MavenDomProjectModel model) {
        Set<MavenDomProjectModel> models = new HashSet<>();

        collectChildrenProjects(model, models);

        return models;
    }

    @RequiredReadAction
    private static void collectChildrenProjects(@Nonnull final MavenDomProjectModel model, @Nonnull Set<MavenDomProjectModel> models) {
        MavenProject mavenProject = MavenDomUtil.findProject(model);
        if (mavenProject != null) {
            final Project project = model.getManager().getProject();
            for (MavenProject inheritor : MavenProjectsManager.getInstance(project).findInheritors(mavenProject)) {
                MavenDomProjectModel inheritorProjectModel = MavenDomUtil.getMavenDomProjectModel(project, inheritor.getFile());
                if (inheritorProjectModel != null && !models.contains(inheritorProjectModel)) {
                    models.add(inheritorProjectModel);
                    collectChildrenProjects(inheritorProjectModel, models);
                }
            }
        }
    }

    @Nonnull
    @RequiredReadAction
    public static Set<MavenDomProjectModel> collectParentProjects(@Nonnull final MavenDomProjectModel projectDom) {
        final Set<MavenDomProjectModel> parents = new HashSet<>();

        Processor<MavenDomProjectModel> collectProcessor = model -> {
            parents.add(model);
            return false;
        };
        processParentProjects(projectDom, collectProcessor);

        return parents;
    }

    @RequiredReadAction
    public static void processParentProjects(
        @Nonnull final MavenDomProjectModel projectDom,
        @Nonnull final Processor<MavenDomProjectModel> processor
    ) {
        Set<MavenDomProjectModel> processed = new HashSet<>();
        Project project = projectDom.getManager().getProject();
        MavenDomProjectModel parent = findParent(projectDom, project);
        while (parent != null) {
            if (processed.contains(parent)) {
                break;
            }
            processed.add(parent);
            if (processor.process(parent)) {
                break;
            }

            parent = findParent(parent, project);
        }
    }

    @Nullable
    @RequiredReadAction
    public static MavenDomProjectModel findParent(@Nonnull MavenDomProjectModel model, Project project) {
        return findParent(model.getMavenParent(), project);
    }

    @Nullable
    @RequiredReadAction
    public static MavenDomProjectModel findParent(@Nonnull MavenDomParent mavenDomParent, Project project) {
        if (!DomUtil.hasXml(mavenDomParent)) {
            return null;
        }

        MavenId id = new MavenId(mavenDomParent.getGroupId().getStringValue(), mavenDomParent.getArtifactId().getStringValue(),
            mavenDomParent.getVersion().getStringValue()
        );
        MavenProject mavenProject = MavenProjectsManager.getInstance(project).findProject(id);

        return mavenProject != null ? MavenDomUtil.getMavenDomProjectModel(project, mavenProject.getFile()) : null;
    }

    @Nullable
    @RequiredReadAction
    public static XmlTag searchProperty(
        @Nonnull final String propertyName,
        @Nonnull MavenDomProjectModel projectDom,
        @Nonnull final Project project
    ) {
        SearchProcessor<XmlTag, MavenDomProperties> searchProcessor = new SearchProcessor<>() {
            @Override
            protected XmlTag find(MavenDomProperties element) {
                return findProperty(element, propertyName);
            }
        };

        processProperties(projectDom, searchProcessor, project);
        return searchProcessor.myResult;
    }

    @Nullable
    public static XmlTag findProperty(@Nonnull MavenDomProperties mavenDomProperties, @Nonnull String propertyName) {
        XmlTag propertiesTag = mavenDomProperties.getXmlTag();
        if (propertiesTag == null) {
            return null;
        }

        for (XmlTag each : propertiesTag.getSubTags()) {
            if (each.getName().equals(propertyName)) {
                return each;
            }
        }

        return null;
    }

    @RequiredReadAction
    public static Set<XmlTag> collectProperties(@Nonnull MavenDomProjectModel projectDom, @Nonnull final Project project) {
        final Set<XmlTag> properties = new HashSet<>();

        Processor<MavenDomProperties> collectProcessor = mavenDomProperties -> {
            XmlTag propertiesTag = mavenDomProperties.getXmlTag();
            if (propertiesTag != null) {
                ContainerUtil.addAll(properties, propertiesTag.getSubTags());
            }
            return false;
        };

        processProperties(projectDom, collectProcessor, project);

        return properties;
    }

    @Nonnull
    @RequiredReadAction
    public static Set<MavenDomDependency> searchDependencyUsages(@Nonnull final MavenDomDependency dependency) {
        final MavenDomProjectModel model = dependency.getParentOfType(MavenDomProjectModel.class, false);
        if (model != null) {
            DependencyConflictId dependencyId = DependencyConflictId.create(dependency);
            if (dependencyId != null) {
                return searchDependencyUsages(model, dependencyId, Collections.singleton(dependency));
            }
        }
        return Collections.emptySet();
    }

    @Nonnull
    @RequiredReadAction
    public static Set<MavenDomDependency> searchDependencyUsages(
        @Nonnull final MavenDomProjectModel model,
        @Nonnull final DependencyConflictId dependencyId,
        @Nonnull final Set<MavenDomDependency> excludes
    ) {
        Project project = model.getManager().getProject();
        final Set<MavenDomDependency> usages = new HashSet<>();
        Processor<MavenDomProjectModel> collectProcessor = mavenDomProjectModel -> {
            if (!model.equals(mavenDomProjectModel)) {
                for (MavenDomDependency domDependency : mavenDomProjectModel.getDependencies().getDependencies()) {
                    if (excludes.contains(domDependency)) {
                        continue;
                    }

                    if (dependencyId.equals(DependencyConflictId.create(domDependency))) {
                        usages.add(domDependency);
                    }
                }
            }
            return false;
        };

        processChildrenRecursively(model, collectProcessor, project, new HashSet<>(), true);

        return usages;
    }

    @Nonnull
    @RequiredReadAction
    public static Collection<MavenDomPlugin> searchManagedPluginUsages(@Nonnull final MavenDomPlugin plugin) {
        String artifactId = plugin.getArtifactId().getStringValue();
        if (artifactId == null) {
            return Collections.emptyList();
        }

        String groupId = plugin.getGroupId().getStringValue();

        MavenDomProjectModel model = plugin.getParentOfType(MavenDomProjectModel.class, false);
        if (model == null) {
            return Collections.emptyList();
        }

        return searchManagedPluginUsages(model, groupId, artifactId);
    }

    @Nonnull
    @RequiredReadAction
    public static Collection<MavenDomPlugin> searchManagedPluginUsages(
        @Nonnull final MavenDomProjectModel model,
        @Nullable final String groupId,
        @Nonnull final String artifactId
    ) {
        Project project = model.getManager().getProject();

        final Set<MavenDomPlugin> usages = new HashSet<>();

        Processor<MavenDomProjectModel> collectProcessor = mavenDomProjectModel -> {
            for (MavenDomPlugin domPlugin : mavenDomProjectModel.getBuild().getPlugins().getPlugins()) {
                if (MavenPluginDomUtil.isPlugin(domPlugin, groupId, artifactId)) {
                    usages.add(domPlugin);
                }
            }
            return false;
        };

        processChildrenRecursively(model, collectProcessor, project, new HashSet<>(), true);

        return usages;
    }

    @RequiredReadAction
    public static void processChildrenRecursively(
        @Nullable MavenDomProjectModel model,
        @Nonnull Processor<MavenDomProjectModel> processor
    ) {
        processChildrenRecursively(model, processor, true);
    }

    @RequiredReadAction
    public static void processChildrenRecursively(
        @Nullable MavenDomProjectModel model,
        @Nonnull Processor<MavenDomProjectModel> processor,
        boolean processCurrentModel
    ) {
        if (model != null) {
            processChildrenRecursively(
                model,
                processor,
                model.getManager().getProject(),
                new HashSet<>(),
                processCurrentModel
            );
        }
    }

    @RequiredReadAction
    public static void processChildrenRecursively(
        @Nullable MavenDomProjectModel model,
        @Nonnull Processor<MavenDomProjectModel> processor,
        @Nonnull Project project,
        @Nonnull Set<MavenDomProjectModel> processedModels,
        boolean strict
    ) {
        if (model != null && !processedModels.contains(model)) {
            processedModels.add(model);

            if (strict && processor.process(model)) {
                return;
            }

            MavenProject mavenProject = MavenDomUtil.findProject(model);
            if (mavenProject != null) {
                for (MavenProject childProject : MavenProjectsManager.getInstance(project).findInheritors(mavenProject)) {
                    MavenDomProjectModel childProjectModel = MavenDomUtil.getMavenDomProjectModel(project, childProject.getFile());

                    processChildrenRecursively(childProjectModel, processor, project, processedModels, true);
                }
            }
        }
    }

    @Nullable
    @RequiredReadAction
    public static MavenDomDependency searchManagingDependency(@Nonnull final MavenDomDependency dependency) {
        return searchManagingDependency(dependency, dependency.getManager().getProject());
    }

    @Nullable
    @RequiredReadAction
    public static MavenDomDependency searchManagingDependency(
        @Nonnull final MavenDomDependency dependency,
        @Nonnull final Project project
    ) {
        final DependencyConflictId depId = DependencyConflictId.create(dependency);
        if (depId == null) {
            return null;
        }

        final MavenDomProjectModel model = dependency.getParentOfType(MavenDomProjectModel.class, false);
        if (model == null) {
            return null;
        }

        final Ref<MavenDomDependency> res = new Ref<>();

        Processor<MavenDomDependency> processor = dependency1 -> {
            if (depId.equals(DependencyConflictId.create(dependency1))) {
                res.set(dependency1);
                return true;
            }

            return false;
        };

        processDependenciesInDependencyManagement(model, processor, project);

        return res.get();
    }

    @Nullable
    @RequiredReadAction
    public static MavenDomPlugin searchManagingPlugin(@Nonnull final MavenDomPlugin plugin) {
        final String artifactId = plugin.getArtifactId().getStringValue();
        final String groupId = plugin.getGroupId().getStringValue();
        if (artifactId == null) {
            return null;
        }

        final MavenDomProjectModel model = plugin.getParentOfType(MavenDomProjectModel.class, false);
        if (model == null) {
            return null;
        }

        SearchProcessor<MavenDomPlugin, MavenDomPlugins> processor = new SearchProcessor<>() {
            @Override
            protected MavenDomPlugin find(MavenDomPlugins mavenDomPlugins) {
                if (!model.equals(mavenDomPlugins.getParentOfType(MavenDomProjectModel.class, true))) {
                    for (MavenDomPlugin domPlugin : mavenDomPlugins.getPlugins()) {
                        if (MavenPluginDomUtil.isPlugin(domPlugin, groupId, artifactId)) {
                            return domPlugin;
                        }
                    }
                }

                return null;
            }
        };

        Function<MavenDomProjectModelBase, MavenDomPlugins> domProfileFunction =
            mavenDomProfile -> mavenDomProfile.getBuild().getPluginManagement().getPlugins();

        process(model, processor, model.getManager().getProject(), domProfileFunction, domProfileFunction);

        return processor.myResult;
    }

    @RequiredReadAction
    public static boolean processDependenciesInDependencyManagement(
        @Nonnull MavenDomProjectModel projectDom,
        @Nonnull final Processor<MavenDomDependency> processor,
        @Nonnull final Project project
    ) {
        Processor<MavenDomDependencies> managedDependenciesListProcessor = dependencies -> {
            SmartList<MavenDomDependency> importDependencies = null;

            for (MavenDomDependency domDependency : dependencies.getDependencies()) {
                if ("import".equals(domDependency.getScope().getRawText())) {
                    if (importDependencies == null) {
                        importDependencies = new SmartList<>();
                    }

                    importDependencies.add(domDependency);
                }
                else if (processor.process(domDependency)) {
                    return true;
                }
            }

            if (importDependencies != null) {
                for (MavenDomDependency domDependency : importDependencies) {
                    GenericDomValue<String> version = domDependency.getVersion();
                    if (version.getXmlElement() != null) {
                        GenericDomValueReference<String> reference = new GenericDomValueReference<>(version);
                        PsiElement resolve = reference.resolve();

                        if (resolve instanceof XmlFile xmlFile) {
                            MavenDomProjectModel dependModel = MavenDomUtil.getMavenDomModel(xmlFile, MavenDomProjectModel.class);
                            if (dependModel != null) {
                                for (MavenDomDependency dep : dependModel.getDependencyManagement().getDependencies().getDependencies()) {
                                    if (processor.process(dep)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return false;
        };

        Function<MavenDomProjectModelBase, MavenDomDependencies> domFunction =
            mavenDomProfile -> mavenDomProfile.getDependencyManagement().getDependencies();

        return process(projectDom, managedDependenciesListProcessor, project, domFunction, domFunction);
    }

    @RequiredReadAction
    public static boolean processDependencies(
        @Nonnull MavenDomProjectModel projectDom,
        @Nonnull final Processor<MavenDomDependencies> processor
    ) {
        Function<MavenDomProjectModelBase, MavenDomDependencies> domFunction = MavenDomProjectModelBase::getDependencies;

        return process(projectDom, processor, projectDom.getManager().getProject(), domFunction, domFunction);
    }

    @RequiredReadAction
    public static boolean processProperties(
        @Nonnull MavenDomProjectModel projectDom,
        @Nonnull final Processor<MavenDomProperties> processor,
        @Nonnull final Project project
    ) {
        Function<MavenDomProjectModelBase, MavenDomProperties> domFunction = MavenDomProjectModelBase::getProperties;

        return process(projectDom, processor, project, domFunction, domFunction);
    }

    @RequiredReadAction
    public static <T> boolean process(
        @Nonnull MavenDomProjectModel projectDom,
        @Nonnull final Processor<T> processor,
        @Nonnull final Project project,
        @Nonnull final Function<? super MavenDomProfile, T> domProfileFunction,
        @Nonnull final Function<? super MavenDomProjectModel, T> projectDomFunction
    ) {
        return process(projectDom, processor, project, domProfileFunction, projectDomFunction, new HashSet<>());
    }

    @RequiredReadAction
    public static <T> boolean process(
        @Nonnull MavenDomProjectModel projectDom,
        @Nonnull final Processor<T> processor,
        @Nonnull final Project project,
        @Nonnull final Function<? super MavenDomProfile, T> domProfileFunction,
        @Nonnull final Function<? super MavenDomProjectModel, T> projectDomFunction,
        final Set<MavenDomProjectModel> processed
    ) {
        if (processed.contains(projectDom)) {
            return true;
        }
        processed.add(projectDom);

        MavenProject mavenProjectOrNull = MavenDomUtil.findProject(projectDom);

        if (processSettingsXml(mavenProjectOrNull, processor, project, domProfileFunction)) {
            return true;
        }
        if (processProject(projectDom, mavenProjectOrNull, processor, project, domProfileFunction, projectDomFunction)) {
            return true;
        }

        return processParentProjectFile(projectDom, processor, project, domProfileFunction, projectDomFunction, processed);
    }

    @RequiredReadAction
    private static <T> boolean processParentProjectFile(
        MavenDomProjectModel projectDom,
        final Processor<T> processor,
        final Project project,
        final Function<? super MavenDomProfile, T> domProfileFunction,
        final Function<? super MavenDomProjectModel, T> projectDomFunction,
        final Set<MavenDomProjectModel> processed
    ) {
        Boolean aBoolean = new DomParentProjectFileProcessor<Boolean>(MavenProjectsManager.getInstance(project)) {
            @Override
            @RequiredReadAction
            protected Boolean doProcessParent(VirtualFile parentFile) {
                MavenDomProjectModel parentProjectDom = MavenDomUtil.getMavenDomProjectModel(project, parentFile);
                return parentProjectDom != null && MavenDomProjectProcessorUtils.process(
                    parentProjectDom,
                    processor,
                    project,
                    domProfileFunction,
                    projectDomFunction,
                    processed
                );
            }
        }.process(projectDom);

        return aBoolean != null && aBoolean;
    }

    @RequiredReadAction
    private static <T> boolean processSettingsXml(
        @Nullable MavenProject mavenProject,
        @Nonnull Processor<T> processor,
        @Nonnull Project project,
        Function<? super MavenDomProfile, T> domProfileFunction
    ) {
        MavenGeneralSettings settings = MavenProjectsManager.getInstance(project).getGeneralSettings();

        for (VirtualFile each : settings.getEffectiveSettingsFiles()) {
            MavenDomSettingsModel settingsDom = MavenDomUtil.getMavenDomModel(project, each, MavenDomSettingsModel.class);
            if (settingsDom == null) {
                continue;
            }

            if (processProfiles(settingsDom.getProfiles(), mavenProject, processor, domProfileFunction)) {
                return true;
            }
        }
        return false;
    }

    @RequiredReadAction
    private static <T> boolean processProject(
        MavenDomProjectModel projectDom,
        MavenProject mavenProjectOrNull,
        Processor<T> processor,
        Project project,
        Function<? super MavenDomProfile, T> domProfileFunction,
        Function<? super MavenDomProjectModel, T> projectDomFunction
    ) {
        if (processProfilesXml(MavenDomUtil.getVirtualFile(projectDom), mavenProjectOrNull, processor, project, domProfileFunction)) {
            return true;
        }

        if (processProfiles(projectDom.getProfiles(), mavenProjectOrNull, processor, domProfileFunction)) {
            return true;
        }

        T t = projectDomFunction.apply(projectDom);
        return t != null && processor.process(t);
    }

    @RequiredReadAction
    private static <T> boolean processProfilesXml(
        VirtualFile projectFile,
        MavenProject mavenProjectOrNull,
        Processor<T> processor,
        Project project,
        Function<? super MavenDomProfile, T> f
    ) {
        VirtualFile profilesFile = MavenUtil.findProfilesXmlFile(projectFile);
        if (profilesFile == null) {
            return false;
        }

        MavenDomProfiles profiles = MavenDomUtil.getMavenDomProfilesModel(project, profilesFile);
        return profiles != null && processProfiles(profiles, mavenProjectOrNull, processor, f);
    }

    private static <T> boolean processProfiles(
        MavenDomProfiles profilesDom,
        MavenProject mavenProjectOrNull,
        Processor<T> processor,
        Function<? super MavenDomProfile, T> f
    ) {
        Collection<String> activePropfiles =
            mavenProjectOrNull == null ? null : mavenProjectOrNull.getActivatedProfilesIds().getEnabledProfiles();
        for (MavenDomProfile each : profilesDom.getProfiles()) {
            XmlTag idTag = each.getId().getXmlTag();
            if (idTag == null) {
                continue;
            }
            if (activePropfiles != null && !activePropfiles.contains(idTag.getValue().getTrimmedText())) {
                continue;
            }

            if (processProfile(each, processor, f)) {
                return true;
            }
        }
        return false;
    }

    private static <T> boolean processProfile(MavenDomProfile profileDom, Processor<T> processor, Function<? super MavenDomProfile, T> f) {
        T t = f.apply(profileDom);
        return t != null && processor.process(t);
    }

    public abstract static class DomParentProjectFileProcessor<T> extends MavenParentProjectFileProcessor<T> {
        private final MavenProjectsManager myManager;

        public DomParentProjectFileProcessor(MavenProjectsManager manager) {
            myManager = manager;
        }

        @Override
        protected VirtualFile findManagedFile(@Nonnull MavenId id) {
            MavenProject project = myManager.findProject(id);
            return project == null ? null : project.getFile();
        }

        @Nullable
        public T process(@Nonnull MavenDomProjectModel projectDom) {
            MavenDomParent parent = projectDom.getMavenParent();
            MavenParentDesc parentDesc = null;
            if (DomUtil.hasXml(parent)) {
                String parentGroupId = parent.getGroupId().getStringValue();
                String parentArtifactId = parent.getArtifactId().getStringValue();
                String parentVersion = parent.getVersion().getStringValue();
                String parentRelativePath = parent.getRelativePath().getStringValue();
                if (StringUtil.isEmptyOrSpaces(parentRelativePath)) {
                    parentRelativePath = "../pom.xml";
                }
                MavenId parentId = new MavenId(parentGroupId, parentArtifactId, parentVersion);
                parentDesc = new MavenParentDesc(parentId, parentRelativePath);
            }

            return process(myManager.getGeneralSettings(), MavenDomUtil.getVirtualFile(projectDom), parentDesc);
        }
    }

    public abstract static class SearchProcessor<R, T> implements Processor<T> {
        private R myResult;

        @Override
        public final boolean process(T t) {
            R res = find(t);
            if (res != null) {
                myResult = res;
                return true;
            }

            return false;
        }

        @Nullable
        protected abstract R find(T element);

        public R getResult() {
            return myResult;
        }
    }
}
