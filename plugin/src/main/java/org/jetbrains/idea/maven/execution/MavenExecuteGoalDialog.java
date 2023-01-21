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

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.Action;

import consulo.project.Project;

/**
 * @author Sergey Evdokimov
 */
public class MavenExecuteGoalDialog extends MavenEditGoalDialog
{
	public MavenExecuteGoalDialog(@Nonnull Project project, @Nullable Collection<String> history)
	{
		super(project, history);

		setTitle("Execute Maven Goal");
	}

	@Nonnull
	@Override
	protected Action getOKAction()
	{
		Action action = super.getOKAction();
		action.putValue(Action.NAME, "&Execute");
		return action;
	}
}
