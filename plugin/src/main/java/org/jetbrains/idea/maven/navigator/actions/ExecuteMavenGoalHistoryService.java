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
package org.jetbrains.idea.maven.navigator.actions;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
@Singleton
@State(name = "mavenExecuteGoalHistory", storages = @Storage(file = StoragePathMacros.WORKSPACE_FILE))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class ExecuteMavenGoalHistoryService implements PersistentStateComponent<String[]> {
    @Nonnull
    public static ExecuteMavenGoalHistoryService getInstance(@Nonnull Project project) {
        return ServiceManager.getService(project, ExecuteMavenGoalHistoryService.class);
    }

    private static final int MAX_HISTORY_LENGTH = 20;

    private final LinkedList<String> myHistory = new LinkedList<>();

    private String myWorkDirectory = "";

    private String myCanceledCommand;

    @Nullable
    public String getCanceledCommand() {
        return myCanceledCommand;
    }

    public void setCanceledCommand(@Nullable String canceledCommand) {
        myCanceledCommand = canceledCommand;
    }

    public void addCommand(@Nonnull String command, @Nonnull String projectPath) {
        myWorkDirectory = projectPath.trim();

        command = command.trim();

        if (command.length() == 0) {
            return;
        }

        myHistory.remove(command);
        myHistory.addFirst(command);

        while (myHistory.size() > MAX_HISTORY_LENGTH) {
            myHistory.removeLast();
        }
    }

    public List<String> getHistory() {
        return new ArrayList<>(myHistory);
    }

    @Nonnull
    public String getWorkDirectory() {
        return myWorkDirectory;
    }

    @Nullable
    @Override
    public String[] getState() {
        String[] res = new String[myHistory.size() + 1];
        res[0] = myWorkDirectory;

        int i = 1;
        for (String goal : myHistory) {
            res[i++] = goal;
        }

        return res;
    }

    @Override
    public void loadState(String[] state) {
        if (state.length == 0) {
            myWorkDirectory = "";
            myHistory.clear();
        }
        else {
            myWorkDirectory = state[0];
            myHistory.addAll(Arrays.asList(state).subList(1, state.length));
        }
    }
}
