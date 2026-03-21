// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.build;

import consulo.execution.ExecutionResult;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.runner.DefaultProgramRunner;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.RunContentBuilder;
import consulo.execution.ui.RunContentDescriptor;
import consulo.process.ExecutionException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

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
    public boolean canRun(@Nonnull String executorId, @Nonnull consulo.execution.configuration.RunProfile profile) {
        return true;
    }

    @Override
    @Nullable
    protected RunContentDescriptor doExecute(RunProfileState state, ExecutionEnvironment env) throws ExecutionException {
        ExecutionResult executionResult = state.execute(env.getExecutor(), this);
        if (executionResult == null) {
            return null;
        }
        return new RunContentBuilder(executionResult, env).showRunContent(env.getContentToReuse());
    }
}
