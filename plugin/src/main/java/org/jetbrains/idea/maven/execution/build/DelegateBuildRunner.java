// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.build;

import consulo.application.Application;
import consulo.execution.ExecutionResult;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.execution.runner.RunContentBuilder;
import consulo.execution.ui.RunContentDescriptor;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public class DelegateBuildRunner implements ProgramRunner<Object> {
    private static final String ID = "MAVEN_DELEGATE_BUILD_RUNNER";
    private static final Logger LOG = Logger.getInstance(DelegateBuildRunner.class);

    @Nonnull
    @Override
    public String getRunnerId() {
        return ID;
    }

    @Override
    public boolean canRun(@Nonnull String executorId, @Nonnull consulo.execution.configuration.RunProfile profile) {
        return true;
    }

    @Nullable
    protected RunContentDescriptor doExecute(@Nonnull RunProfileState state, @Nonnull ExecutionEnvironment environment) throws ExecutionException {
        ExecutionResult executionResult = state.execute(environment.getExecutor(), this);
        if (executionResult == null) {
            return null;
        }

        AtomicReference<RunContentDescriptor> result = new AtomicReference<>();
        Application.get().invokeAndWait(() -> {
            RunContentBuilder runContentBuilder = new RunContentBuilder(executionResult, environment);

            RunContentDescriptor runContentDescriptor = runContentBuilder.showRunContent(environment.getContentToReuse());
            if (runContentDescriptor == null) {
                return;
            }

            RunContentDescriptor descriptor = new RunContentDescriptor(
                runContentDescriptor.getExecutionConsole(),
                runContentDescriptor.getProcessHandler(),
                runContentDescriptor.getComponent(),
                runContentDescriptor.getDisplayName(),
                (Image) runContentDescriptor.getIcon(),
                null,
                runContentDescriptor.getRestartActions()
            ) {
                @Override
                public boolean isHiddenContent() {
                    return true;
                }
            };
            descriptor.setRunnerLayoutUi(runContentDescriptor.getRunnerLayoutUi());
            result.set(descriptor);
        });
        return result.get();
    }

    @Override
    public void execute(@Nonnull ExecutionEnvironment environment) throws ExecutionException {
        RunProfileState state = environment.getState();
        if (state != null) {
            doExecute(state, environment);
        }
    }

    public static final class Util {
        @Nullable
        public static ProgramRunner<?> getDelegateRunner() {
            return ProgramRunner.findRunnerById(ID);
        }
    }
}
