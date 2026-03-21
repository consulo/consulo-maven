// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.build;

import consulo.document.FileDocumentManager;
import consulo.execution.ExecutionResult;
import consulo.execution.configuration.RunProfile;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.runner.DefaultProgramRunner;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.execution.ui.RunContentDescriptor;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;

import javax.swing.*;

public class DelegateBuildRunner extends DefaultProgramRunner {
    private static final String ID = "MAVEN_DELEGATE_BUILD_RUNNER";

    public static DelegateBuildRunner getDelegateRunner() {
        return new DelegateBuildRunner();
    }

    @Nonnull
    @Override
    public String getRunnerId() {
        return ID;
    }

    @Override
    public boolean canRun(@Nonnull String executorId, @Nonnull RunProfile profile) {
        return true;
    }

    /**
     * Overrides GenericProgramRunner.execute() to skip ExecutionManager.startRunProfile().
     * That call would unconditionally register the descriptor in the Run ToolWindow —
     * but for delegate builds we only want output in the Build ToolWindow (via BuildViewManager
     * inside MavenCommandLineState.doDelegateBuildExecute()).
     *
     * We still create a RunContentDescriptor so callers that set a ProgramRunner.Callback
     * (e.g. MavenCompilerRunner, MavenBeforeRunTasksProvider) can obtain the ProcessHandler.
     */
    @RequiredUIAccess
    @Override
    protected void execute(ExecutionEnvironment environment, RunProfileState state) throws ExecutionException {
        FileDocumentManager.getInstance().saveAllDocuments();

        ExecutionResult executionResult = state.execute(environment.getExecutor(), this);
        if (executionResult == null) {
            ProgramRunner.Callback callback = environment.getCallback();
            if (callback != null) {
                callback.processStarted(null);
            }
            return;
        }

        ProcessHandler processHandler = executionResult.getProcessHandler();

        // Build a descriptor for the callback only — do NOT register it with RunContentManager.
        // Build output reaches the Build ToolWindow through BuildViewManager events set up in
        // MavenCommandLineState.doDelegateBuildExecute().
        RunContentDescriptor descriptor = new RunContentDescriptor(
            executionResult.getExecutionConsole(),
            processHandler,
            new JPanel(),
            environment.getRunProfile().getName(),
            null
        );
        descriptor.setExecutionId(environment.getExecutionId());

        ProgramRunner.Callback callback = environment.getCallback();
        if (callback != null) {
            callback.processStarted(descriptor);
        }

        if (processHandler != null && !processHandler.isStartNotified()) {
            processHandler.startNotify();
        }
    }
}
