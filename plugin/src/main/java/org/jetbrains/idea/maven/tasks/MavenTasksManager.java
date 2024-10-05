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
package org.jetbrains.idea.maven.tasks;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.compiler.CompileContext;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.execution.RunManager;
import consulo.project.Project;
import consulo.util.collection.Lists;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
@State(name = "MavenCompilerTasksManager", storages = @Storage("misc.xml"))
@ServiceAPI(value = ComponentScope.PROJECT, lazy = false)
@ServiceImpl
public class MavenTasksManager extends MavenSimpleProjectComponent implements PersistentStateComponent<MavenTasksManagerState> {
    private final AtomicBoolean isInitialized = new AtomicBoolean();

    private MavenTasksManagerState myState = new MavenTasksManagerState();

    private final MavenProjectsManager myProjectsManager;
    private final MavenRunner myRunner;

    private final List<Listener> myListeners = Lists.newLockFreeCopyOnWriteList();

    public static MavenTasksManager getInstance(Project project) {
        return project.getComponent(MavenTasksManager.class);
    }

    @Inject
    public MavenTasksManager(Project project, MavenProjectsManager projectsManager, MavenRunner runner) {
        super(project);
        myProjectsManager = projectsManager;
        myRunner = runner;
    }

    @Override
    public synchronized MavenTasksManagerState getState() {
        MavenTasksManagerState result = new MavenTasksManagerState();
        result.afterCompileTasks = new HashSet<>(myState.afterCompileTasks);
        result.beforeCompileTasks = new HashSet<>(myState.beforeCompileTasks);
        return result;
    }

    @Override
    public void loadState(MavenTasksManagerState state) {
        synchronized (this) {
            myState = state;
        }
        if (isInitialized.get()) {
            fireTasksChanged();
        }
    }

    @Override
    public void afterLoadState() {
        if (!isNormalProject()) {
            return;
        }
        isInitialized.set(true);
    }

    public boolean doExecute(boolean before, CompileContext context) {
        List<MavenRunnerParameters> parametersList;
        synchronized (this) {
            parametersList = new ArrayList<>();
            Set<MavenCompilerTask> tasks = before ? myState.beforeCompileTasks : myState.afterCompileTasks;
            for (MavenCompilerTask each : tasks) {
                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(each.getProjectPath());
                if (file == null) {
                    continue;
                }
                parametersList.add(new MavenRunnerParameters(
                    true,
                    file.getParent().getPath(),
                    Arrays.asList(each.getGoal()),
                    myProjectsManager.getExplicitProfiles()
                ));
            }
        }
        return myRunner.runBatch(parametersList, null, null, TasksBundle.message("maven.tasks.executing"), context.getProgressIndicator());
    }

    public synchronized boolean isBeforeCompileTask(MavenCompilerTask task) {
        return myState.beforeCompileTasks.contains(task);
    }

    public void addBeforeCompileTasks(List<MavenCompilerTask> tasks) {
        synchronized (this) {
            myState.beforeCompileTasks.addAll(tasks);
        }
        fireTasksChanged();
    }

    public void removeBeforeCompileTasks(List<MavenCompilerTask> tasks) {
        synchronized (this) {
            myState.beforeCompileTasks.removeAll(tasks);
        }
        fireTasksChanged();
    }

    public synchronized boolean isAfterCompileTask(MavenCompilerTask task) {
        return myState.afterCompileTasks.contains(task);
    }

    public void addAfterCompileTasks(List<MavenCompilerTask> tasks) {
        synchronized (this) {
            myState.afterCompileTasks.addAll(tasks);
        }
        fireTasksChanged();
    }

    public void removeAfterCompileTasks(List<MavenCompilerTask> tasks) {
        synchronized (this) {
            myState.afterCompileTasks.removeAll(tasks);
        }
        fireTasksChanged();
    }

    public String getDescription(MavenProject project, String goal) {
        List<String> result = new ArrayList<>();
        MavenCompilerTask compilerTask = new MavenCompilerTask(project.getPath(), goal);
        synchronized (this) {
            if (myState.beforeCompileTasks.contains(compilerTask)) {
                result.add(TasksBundle.message("maven.tasks.goal.before.compile"));
            }
            if (myState.afterCompileTasks.contains(compilerTask)) {
                result.add(TasksBundle.message("maven.tasks.goal.after.compile"));
            }
        }
        RunManager runManager = RunManager.getInstance(myProject);
        for (MavenBeforeRunTask each : runManager.getBeforeRunTasks(MavenBeforeRunTasksProvider.ID)) {
            if (each.isFor(project, goal)) {
                result.add(TasksBundle.message("maven.tasks.goal.before.run"));
                break;
            }
        }

        return StringUtil.join(result, ", ");
    }

    public void addListener(Listener l) {
        myListeners.add(l);
    }

    public void fireTasksChanged() {
        for (Listener each : myListeners) {
            each.compileTasksChanged();
        }
    }

    public interface Listener {
        void compileTasksChanged();
    }
}