/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import consulo.maven.icon.MavenIconGroup;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.PopupStep;
import consulo.ui.image.Image;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenProjectNamer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MavenSelectProjectPopup {
    public static JBPopup buildPopup(MavenProjectsManager projectsManager, Consumer<MavenProject> callback) {
        List<MavenProject> projectList = projectsManager.getProjects();
        if (projectList.isEmpty()) {
            return JBPopupFactory.getInstance().createMessage(MavenRunnerLocalize.mavenRunnerNoMavenProjects().get());
        }

        Map<MavenProject, String> projectsNameMap = MavenProjectNamer.generateNameMap(projectList);

        return JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(
            MavenRunnerLocalize.mavenRunnerSelectMavenProject().get(),
            orderByAggregators(projectsManager, projectList)
        ) {
            @Override
            public String getTextFor(MavenProject value) {
                return projectsNameMap.get(value);
            }

            @Override
            public Image getIconFor(MavenProject value) {
                return MavenIconGroup.mavenlogo();
            }

            @Override
            public boolean isSpeedSearchEnabled() {
                return true;
            }

            @Override
            public PopupStep onChosen(MavenProject selectedValue, boolean finalChoice) {
                return doFinalStep(() -> callback.accept(selectedValue));
            }
        });
    }

    private static List<MavenProject> orderByAggregators(MavenProjectsManager projectsManager, List<MavenProject> projectList) {
        List<MavenProject> projects = new ArrayList<>(projectList);
        projects.sort(new MavenProjectNamer.MavenProjectComparator());

        Map<MavenProject, List<MavenProject>> modules = new LinkedHashMap<>();
        List<MavenProject> roots = new ArrayList<>();
        for (MavenProject project : projects) {
            MavenProject aggregator = projectsManager.findAggregator(project);
            if (aggregator != null) {
                modules.computeIfAbsent(aggregator, it -> new ArrayList<>()).add(project);
            }
            else {
                roots.add(project);
            }
        }

        List<MavenProject> result = new ArrayList<>(projects.size());
        for (MavenProject root : roots) {
            collect(root, modules, result);
        }
        return result;
    }

    private static void collect(MavenProject project, Map<MavenProject, List<MavenProject>> modules, List<MavenProject> result) {
        result.add(project);
        for (MavenProject module : modules.getOrDefault(project, List.of())) {
            collect(module, modules, result);
        }
    }
}
