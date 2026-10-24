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
package org.jetbrains.idea.maven.tasks.actions;

import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.BeforeRunTask;
import consulo.execution.RunManager;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.localize.ExecutionLocalize;
import consulo.project.Project;
import consulo.ui.CheckBoxTree;
import consulo.ui.CheckBoxTreePolicy;
import consulo.ui.Component;
import consulo.ui.TreeModel;
import consulo.ui.TreeNode;
import consulo.ui.UIAccess;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.layout.ScrollableLayout;
import org.jetbrains.idea.maven.localize.MavenTasksLocalize;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTask;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Picks the run configurations, and the configuration types whose templates, run a maven goal before they launch.
 */
public class MavenExecuteBeforeRunDialog extends DialogDescriptor {
    private final Project myProject;
    private final MavenProject myMavenProject;
    private final String myGoal;
    private final Map<ConfigurationType, List<RunConfiguration>> myConfigurations = new LinkedHashMap<>();

    private @Nullable CheckBoxTree<Object> myTree;

    private MavenExecuteBeforeRunDialog(Project project, MavenProject mavenProject, String goal) {
        super(ExecutionLocalize.executeBeforeRunDebugDialogTitle(MavenTasksLocalize.mavenTasksGoal()));
        myProject = project;
        myMavenProject = mavenProject;
        myGoal = goal;

        RunManager runManager = RunManager.getInstance(project);
        for (ConfigurationType type : runManager.getConfigurationFactories()) {
            List<RunConfiguration> configurations = new ArrayList<>();
            Set<String> addedNames = new HashSet<>();
            for (RunConfiguration configuration : runManager.getConfigurations(type)) {
                if (addedNames.add(configuration.getName())) {
                    configurations.add(configuration);
                }
            }
            myConfigurations.put(type, configurations);
        }
    }

    @RequiredUIAccess
    public static CompletableFuture<?> show(Project project, MavenProject mavenProject, String goal) {
        MavenExecuteBeforeRunDialog descriptor = new MavenExecuteBeforeRunDialog(project, mavenProject, goal);
        return Application.get().getInstance(DialogService.class)
            .build(descriptor)
            .showAsync()
            .thenAcceptAsync(value -> descriptor.apply(), UIAccess.current());
    }

    @Override
    public @Nullable WidthAndHeight getInitialSize() {
        return WidthAndHeight.ofFont(40, 25);
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        CheckBoxTree<Object> tree = CheckBoxTree.create(createModel());
        Disposer.register(uiDisposable, tree.destroyHook());
        tree.setCheckPolicy(CheckBoxTreePolicy.INDEPENDENT);
        myTree = tree;

        for (Map.Entry<ConfigurationType, List<RunConfiguration>> entry : myConfigurations.entrySet()) {
            if (isAssigned(entry.getKey())) {
                tree.setChecked(entry.getKey(), true);
            }
            for (RunConfiguration configuration : entry.getValue()) {
                if (isAssigned(configuration)) {
                    tree.setChecked(configuration, true);
                }
            }
        }

        return ScrollableLayout.create(tree);
    }

    private TreeModel<Object> createModel() {
        return (nodeFactory, parentValue) -> {
            if (parentValue == null) {
                for (ConfigurationType type : myConfigurations.keySet()) {
                    TreeNode<Object> node = nodeFactory.apply(type);
                    node.setLeaf(myConfigurations.get(type).isEmpty());
                    node.setRenderer((value, presentation) -> {
                        presentation.withIcon(type.getIcon());
                        presentation.append(type.getDisplayName());
                    });
                }
            }
            else if (parentValue instanceof ConfigurationType type) {
                for (RunConfiguration configuration : myConfigurations.getOrDefault(type, List.of())) {
                    TreeNode<Object> node = nodeFactory.apply(configuration);
                    node.setLeaf(true);
                    node.setRenderer((value, presentation) -> {
                        presentation.withIcon(configuration.getIcon());
                        presentation.append(configuration.getName());
                    });
                }
            }
        };
    }

    @RequiredUIAccess
    private void apply() {
        CheckBoxTree<Object> tree = myTree;
        if (tree == null) {
            return;
        }

        RunManager runManager = RunManager.getInstance(myProject);
        for (Map.Entry<ConfigurationType, List<RunConfiguration>> entry : myConfigurations.entrySet()) {
            boolean typeChecked = tree.isChecked(entry.getKey());
            for (ConfigurationFactory factory : entry.getKey().getConfigurationFactories()) {
                update(runManager, runManager.getConfigurationTemplate(factory).getConfiguration(), typeChecked);
            }
            for (RunConfiguration configuration : entry.getValue()) {
                update(runManager, configuration, tree.isChecked(configuration));
            }
        }
    }

    private boolean isAssigned(ConfigurationType type) {
        RunManager runManager = RunManager.getInstance(myProject);
        for (ConfigurationFactory factory : type.getConfigurationFactories()) {
            if (isAssigned(runManager.getConfigurationTemplate(factory).getConfiguration())) {
                return true;
            }
        }
        return false;
    }

    private boolean isAssigned(RunConfiguration configuration) {
        for (MavenBeforeRunTask task : RunManager.getInstance(myProject).getBeforeRunTasks(configuration, MavenBeforeRunTask.class)) {
            if (task.isFor(myMavenProject, myGoal)) {
                return true;
            }
        }
        return false;
    }

    private void update(RunManager runManager, RunConfiguration configuration, boolean enabled) {
        if (isAssigned(configuration) == enabled) {
            return;
        }

        List<BeforeRunTask> tasks = new ArrayList<>(runManager.getBeforeRunTasks(configuration));
        if (enabled) {
            MavenBeforeRunTask task = new MavenBeforeRunTask();
            task.setProjectPath(myMavenProject.getPath());
            task.setGoal(myGoal);
            task.setEnabled(true);
            tasks.add(task);
        }
        else {
            tasks.removeIf(task -> task instanceof MavenBeforeRunTask mavenTask && mavenTask.isFor(myMavenProject, myGoal));
        }
        runManager.setBeforeRunTasks(configuration, tasks, false);
    }
}
