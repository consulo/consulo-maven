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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import consulo.lombok.annotations.ProjectService;

/**
 * @author Sergey Evdokimov
 */
@State(
		name = "mavenExecuteGoalHistory",
		storages = @Storage(file = StoragePathMacros.WORKSPACE_FILE))
@ProjectService
public class ExecuteMavenGoalHistoryService implements PersistentStateComponent<String[]>
{

	private static final int MAX_HISTORY_LENGTH = 20;

	private final LinkedList<String> myHistory = new LinkedList<String>();

	private String myWorkDirectory = "";

	private String myCanceledCommand;

	@Nullable
	public String getCanceledCommand()
	{
		return myCanceledCommand;
	}

	public void setCanceledCommand(@Nullable String canceledCommand)
	{
		myCanceledCommand = canceledCommand;
	}

	public void addCommand(@NotNull String command, @NotNull String projectPath)
	{
		myWorkDirectory = projectPath.trim();

		command = command.trim();

		if(command.length() == 0)
		{
			return;
		}

		myHistory.remove(command);
		myHistory.addFirst(command);

		while(myHistory.size() > MAX_HISTORY_LENGTH)
		{
			myHistory.removeLast();
		}
	}

	public List<String> getHistory()
	{
		return new ArrayList<String>(myHistory);
	}

	@NotNull
	public String getWorkDirectory()
	{
		return myWorkDirectory;
	}

	@Nullable
	@Override
	public String[] getState()
	{
		String[] res = new String[myHistory.size() + 1];
		res[0] = myWorkDirectory;

		int i = 1;
		for(String goal : myHistory)
		{
			res[i++] = goal;
		}

		return res;
	}

	@Override
	public void loadState(String[] state)
	{
		if(state.length == 0)
		{
			myWorkDirectory = "";
			myHistory.clear();
		}
		else
		{
			myWorkDirectory = state[0];
			myHistory.addAll(Arrays.asList(state).subList(1, state.length));
		}
	}
}
