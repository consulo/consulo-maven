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
package org.jetbrains.idea.maven.tasks;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.component.ComponentManager;
import consulo.dataContext.DataContext;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.maven.rt.server.common.model.MavenPlugin;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.keymap.KeymapExtension;
import consulo.ui.ex.keymap.KeymapGroup;
import consulo.ui.ex.keymap.KeymapGroupFactory;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.localize.MavenTasksLocalize;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenPluginInfo;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;

@ExtensionImpl
public class MavenKeymapExtension implements KeymapExtension {
    @Override
    public KeymapGroup createGroup(Predicate<AnAction> condition, ComponentManager project) {
        KeymapGroup result =
            KeymapGroupFactory.getInstance().createGroup(MavenTasksLocalize.mavenTasksActionGroupName().get(), AllIcons.Nodes.ConfigFolder);
        if (project == null) {
            return result;
        }

        Comparator<MavenProject> projectComparator = (o1, o2) -> o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
        Map<MavenProject, Set<Pair<String, String>>> projectToActionsMapping = new TreeMap<>(projectComparator);

        ActionManager actionManager = ActionManager.getInstance();
        for (String eachId : actionManager.getActionIds(getActionPrefix((Project)project, null))) {
            AnAction eachAction = actionManager.getAction(eachId);

            if (!(eachAction instanceof MavenGoalAction)) {
                continue;
            }
            if (condition != null && !condition.test(actionManager.getActionOrStub(eachId))) {
                continue;
            }

            MavenGoalAction mavenAction = (MavenGoalAction)eachAction;
            MavenProject mavenProject = mavenAction.getMavenProject();
            Set<Pair<String, String>> actions = projectToActionsMapping.get(mavenProject);
            if (actions == null) {
                final List<String> projectGoals = collectGoals(mavenProject);
                actions = new TreeSet<>((Comparator<Pair<String, String>>)(o1, o2) -> {
                    String goal1 = o1.getFirst();
                    String goal2 = o2.getFirst();
                    int index1 = projectGoals.indexOf(goal1);
                    int index2 = projectGoals.indexOf(goal2);
                    if (index1 == index2) {
                        return goal1.compareToIgnoreCase(goal2);
                    }
                    return (index1 < index2 ? -1 : 1);
                });
                projectToActionsMapping.put(mavenProject, actions);
            }
            actions.add(Couple.of(mavenAction.getGoal(), eachId));
        }

        for (Map.Entry<MavenProject, Set<Pair<String, String>>> each : projectToActionsMapping.entrySet()) {
            MavenProject mavenProject = each.getKey();
            Set<Pair<String, String>> goalsToActionIds = each.getValue();
            if (goalsToActionIds.isEmpty()) {
                continue;
            }
            KeymapGroup group = KeymapGroupFactory.getInstance().createGroup(mavenProject.getDisplayName(), AllIcons.Nodes.ConfigFolder);
            result.addGroup(group);
            for (Pair<String, String> eachGoalToActionId : goalsToActionIds) {
                group.addActionId(eachGoalToActionId.getSecond());
            }
        }

        return result;
    }

    public static void updateActions(Project project, List<MavenProject> mavenProjects) {
        clearActions(project, mavenProjects);
        createActions(project, mavenProjects);
    }

    private static void createActions(Project project, List<MavenProject> mavenProjects) {
        ActionManager manager = ActionManager.getInstance();
        for (MavenProject eachProject : mavenProjects) {
            String actionIdPrefix = getActionPrefix(project, eachProject);
            for (MavenGoalAction eachAction : collectActions(eachProject)) {
                String id = actionIdPrefix + eachAction.getGoal();
                manager.unregisterAction(id);
                manager.registerAction(id, eachAction);
            }
        }
    }

    private static List<MavenGoalAction> collectActions(MavenProject mavenProject) {
        List<MavenGoalAction> result = new ArrayList<>();
        for (String eachGoal : collectGoals(mavenProject)) {
            result.add(new MavenGoalAction(mavenProject, eachGoal));
        }
        return result;
    }

    public static void clearActions(Project project) {
        ActionManager manager = ActionManager.getInstance();
        for (String each : manager.getActionIds(getActionPrefix(project, null))) {
            manager.unregisterAction(each);
        }
    }

    public static void clearActions(Project project, List<MavenProject> mavenProjects) {
        ActionManager manager = ActionManager.getInstance();
        for (MavenProject eachProject : mavenProjects) {
            for (String eachAction : manager.getActionIds(getActionPrefix(project, eachProject))) {
                manager.unregisterAction(eachAction);
            }
        }
    }

    private static List<String> collectGoals(MavenProject project) {
        LinkedHashSet<String> result = new LinkedHashSet<>(); // may contains similar plugins or somethig
        result.addAll(MavenConstants.PHASES);

        for (MavenPlugin each : project.getDeclaredPlugins()) {
            collectGoals(project.getLocalRepository(), each, result);
        }

        return new ArrayList<>(result);
    }

    private static void collectGoals(File repository, MavenPlugin plugin, LinkedHashSet<String> list) {
        MavenPluginInfo info = MavenArtifactUtil.readPluginInfo(repository, plugin.getMavenId());
        if (info == null) {
            return;
        }

        for (MavenPluginInfo.Mojo m : info.getMojos()) {
            list.add(m.getQualifiedGoal());
        }
    }

    @TestOnly
    public static String getActionPrefix(Project project, MavenProject mavenProject) {
        String pomPath = mavenProject == null ? null : mavenProject.getPath();
        return MavenShortcutsManager.getInstance(project).getActionId(pomPath, null);
    }

    private static class MavenGoalAction extends MavenAction {
        private final MavenProject myMavenProject;
        private final String myGoal;

        public MavenGoalAction(MavenProject mavenProject, String goal) {
            myMavenProject = mavenProject;
            myGoal = goal;
            Presentation template = getTemplatePresentation();
            template.setText(goal, false);
            template.setIcon(PlatformIconGroup.nodesTask());
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(AnActionEvent e) {
            final DataContext context = e.getDataContext();
            MavenRunnerParameters params = new MavenRunnerParameters(
                true,
                myMavenProject.getDirectory(),
                Arrays.asList(myGoal),
                MavenActionUtil.getProjectsManager(context).getExplicitProfiles()
            );
            MavenRunConfigurationType.runConfiguration(MavenActionUtil.getProject(context), params, null);
        }

        public MavenProject getMavenProject() {
            return myMavenProject;
        }

        public String getGoal() {
            return myGoal;
        }

        @Override
        public String toString() {
            return myMavenProject + ":" + myGoal;
        }
    }
}
