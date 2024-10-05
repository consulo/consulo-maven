package org.jetbrains.idea.maven.tasks.compiler;

import consulo.compiler.CompileContext;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import consulo.compiler.CompileTask;

/**
 * @author VISTALL
 * @since 2017-05-09
 */
public class MavenCompileTask implements CompileTask {
    private final boolean myBefore;

    protected MavenCompileTask(boolean before) {
        myBefore = before;
    }

    @Override
    public boolean execute(CompileContext compileContext) {
        MavenTasksManager tasksManager = MavenTasksManager.getInstance(compileContext.getProject());
        return tasksManager.doExecute(myBefore, compileContext);
    }
}
