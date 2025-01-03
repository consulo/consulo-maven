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
package org.jetbrains.idea.maven.dom.inspections;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.util.xml.DomFileElement;
import consulo.xml.util.xml.highlighting.BasicDomElementsInspection;
import consulo.xml.util.xml.highlighting.DomElementAnnotationHolder;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.localize.MavenDomLocalize;
import org.jetbrains.idea.maven.project.MavenProject;

import jakarta.annotation.Nonnull;

import java.util.*;

@ExtensionImpl
public class MavenDuplicateDependenciesInspection extends BasicDomElementsInspection<MavenDomProjectModel, Object> {
    public MavenDuplicateDependenciesInspection() {
        super(MavenDomProjectModel.class);
    }

    @Override
    @RequiredReadAction
    public void checkFileElement(
        DomFileElement<MavenDomProjectModel> domFileElement,
        DomElementAnnotationHolder holder,
        Object state
    ) {
        MavenDomProjectModel projectModel = domFileElement.getRootElement();

        checkMavenProjectModel(projectModel, holder);
    }

    @RequiredReadAction
    private static void checkMavenProjectModel(
        @Nonnull MavenDomProjectModel projectModel,
        @Nonnull DomElementAnnotationHolder holder
    ) {
        final Map<String, Set<MavenDomDependency>> allDuplicates = getDuplicateDependenciesMap(projectModel);

        for (MavenDomDependency dependency : projectModel.getDependencies().getDependencies()) {
            String id = createId(dependency);
            if (id != null) {
                Set<MavenDomDependency> dependencies = allDuplicates.get(id);
                if (dependencies != null && dependencies.size() > 1) {

                    List<MavenDomDependency> duplicatedDependencies = new ArrayList<>();

                    for (MavenDomDependency d : dependencies) {
                        if (d == dependency) {
                            continue;
                        }

                        if (d.getParent() == dependency.getParent()) {
                            duplicatedDependencies.add(d); // Dependencies in same file must be unique by groupId:artifactId:type:classifier
                        }
                        else {
                            if (scope(d).equals(scope(dependency))
                                && Comparing.equal(d.getVersion().getStringValue(), dependency.getVersion().getStringValue())) {
                                // Dependencies in same file must be unique by groupId:artifactId:VERSION:type:classifier:SCOPE
                                duplicatedDependencies.add(d);
                            }
                        }
                    }

                    if (duplicatedDependencies.size() > 0) {
                        addProblem(dependency, duplicatedDependencies, holder);
                    }
                }
            }
        }
    }

    private static String scope(MavenDomDependency dependency) {
        String res = dependency.getScope().getRawText();
        if (StringUtil.isEmpty(res)) {
            return "compile";
        }

        return res;
    }

    @RequiredReadAction
    private static void addProblem(
        @Nonnull MavenDomDependency dependency,
        @Nonnull Collection<MavenDomDependency> dependencies,
        @Nonnull DomElementAnnotationHolder holder
    ) {
        StringBuilder sb = new StringBuilder();
        Set<MavenDomProjectModel> processed = new HashSet<>();
        for (MavenDomDependency domDependency : dependencies) {
            if (dependency.equals(domDependency)) {
                continue;
            }
            MavenDomProjectModel model = domDependency.getParentOfType(MavenDomProjectModel.class, false);
            if (model != null && !processed.contains(model)) {
                if (processed.size() > 0) {
                    sb.append(", ");
                }
                sb.append(createLinkText(model, domDependency));

                processed.add(model);
            }
        }
        holder.createProblem(
            dependency,
            HighlightSeverity.WARNING,
            MavenDomLocalize.mavenduplicatedependenciesinspectionHasDuplicates(sb.toString()).get()
        );
    }

    @RequiredReadAction
    private static String createLinkText(@Nonnull MavenDomProjectModel model, @Nonnull MavenDomDependency dependency) {
        StringBuilder sb = new StringBuilder();

        XmlTag tag = dependency.getXmlTag();
        if (tag == null) {
            return getProjectName(model);
        }
        VirtualFile file = tag.getContainingFile().getVirtualFile();
        if (file == null) {
            return getProjectName(model);
        }

        sb.append("<a href ='#navigation/");
        sb.append(file.getPath());
        sb.append(":");
        sb.append(tag.getTextRange().getStartOffset());
        sb.append("'>");
        sb.append(getProjectName(model));
        sb.append("</a>");

        return sb.toString();
    }

    @Nonnull
    private static String getProjectName(MavenDomProjectModel model) {
        MavenProject mavenProject = MavenDomUtil.findProject(model);
        if (mavenProject != null) {
            return mavenProject.getDisplayName();
        }
        else {
            String name = model.getName().getStringValue();
            if (!StringUtil.isEmptyOrSpaces(name)) {
                return name;
            }
            else {
                return "pom.xml"; // ?
            }
        }
    }

    @Nonnull
    private static Map<String, Set<MavenDomDependency>> getDuplicateDependenciesMap(MavenDomProjectModel projectModel) {
        final Map<String, Set<MavenDomDependency>> allDependencies = new HashMap<>();

        Processor<MavenDomProjectModel> collectProcessor = model -> {
            for (MavenDomDependency dependency : model.getDependencies().getDependencies()) {
                String mavenId = createId(dependency);
                if (mavenId != null) {
                    if (allDependencies.containsKey(mavenId)) {
                        allDependencies.get(mavenId).add(dependency);
                    }
                    else {
                        Set<MavenDomDependency> dependencies = new HashSet<>();
                        dependencies.add(dependency);
                        allDependencies.put(mavenId, dependencies);
                    }
                }
            }
            return false;
        };

        MavenDomProjectProcessorUtils.processChildrenRecursively(projectModel, collectProcessor, true);
        MavenDomProjectProcessorUtils.processParentProjects(projectModel, collectProcessor);

        return allDependencies;
    }

    @Nullable
    private static String createId(MavenDomDependency coordinates) {
        String groupId = coordinates.getGroupId().getStringValue();
        String artifactId = coordinates.getArtifactId().getStringValue();

        if (StringUtil.isEmptyOrSpaces(groupId) || StringUtil.isEmptyOrSpaces(artifactId)) {
            return null;
        }

        String type = coordinates.getType().getStringValue();
        String classifier = coordinates.getClassifier().getStringValue();

        return groupId + ":" + artifactId + ":" + type + ":" + classifier;
    }

    @Nonnull
    @Override
    public String getGroupDisplayName() {
        return MavenDomLocalize.inspectionGroup().get();
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return MavenDomLocalize.inspectionDuplicateDependenciesName().get();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "MavenDuplicateDependenciesInspection";
    }

    @Nonnull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }
}