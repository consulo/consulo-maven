package org.jetbrains.idea.maven.tasks.compiler;

import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;

/**
 * @author VISTALL
 * @since 09-May-17
 */
public class MavenCompileTask implements CompileTask
{
	public static class Before extends MavenCompileTask
	{
		public Before()
		{
			super(true);
		}
	}

	public static class After extends MavenCompileTask
	{
		public After()
		{
			super(false);
		}
	}

	private final boolean myBefore;

	private MavenCompileTask(boolean before)
	{
		myBefore = before;
	}

	@Override
	public boolean execute(CompileContext compileContext)
	{
		MavenTasksManager tasksManager = MavenTasksManager.getInstance(compileContext.getProject());
		return tasksManager.doExecute(myBefore, compileContext);
	}
}
