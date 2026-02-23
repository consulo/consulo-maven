// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.build;

import consulo.execution.RuntimeConfigurationException;
import consulo.execution.configuration.ConfigurationInfoProvider;
import consulo.execution.configuration.ConfigurationPerRunnerSettings;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunnerSettings;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class DelegateBuildRunner implements ProgramRunner<RunnerSettings> {
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
    @Override
    public RunnerSettings createConfigurationData(ConfigurationInfoProvider configurationInfoProvider) {
        return null;
    }

    @Override
    public void checkConfiguration(RunnerSettings runnerSettings, @Nullable ConfigurationPerRunnerSettings configurationPerRunnerSettings) throws RuntimeConfigurationException {

    }

    @Nullable
    @Override
    public SettingsEditor<RunnerSettings> getSettingsEditor(Executor executor, RunConfiguration runConfiguration) {
        return null;
    }

//    @Nullable
//    protected RunContentDescriptor doExecute(@Nonnull RunProfileState state, @Nonnull ExecutionEnvironment environment) throws ExecutionException {
//        ExecutionResult executionResult = state.execute(environment.getExecutor(), this);
//        if (executionResult == null) {
//            return null;
//        }
//
//        AtomicReference<RunContentDescriptor> result = new AtomicReference<>();
//        Application.get().invokeAndWait(() -> {
//            RunContentBuilder runContentBuilder = new RunContentBuilder(executionResult, environment);
//
//            RunContentDescriptor runContentDescriptor = runContentBuilder.showRunContent(environment.getContentToReuse());
//            if (runContentDescriptor == null) {
//                return;
//            }
//
//            RunContentDescriptor descriptor = new RunContentDescriptor(
//                runContentDescriptor.getExecutionConsole(),
//                runContentDescriptor.getProcessHandler(),
//                runContentDescriptor.getComponent(),
//                runContentDescriptor.getDisplayName(),
//                (Image) runContentDescriptor.getIcon(),
//                null,
//                runContentDescriptor.getRestartActions()
//            ) {
//                @Override
//                public boolean isHiddenContent() {
//                    return true;
//                }
//            };
//            descriptor.setRunnerLayoutUi(runContentDescriptor.getRunnerLayoutUi());
//            result.set(descriptor);
//        });
//        return result.get();
//    }

    @Override
    public void execute(@Nonnull ExecutionEnvironment environment) throws ExecutionException {
        throw new UnsupportedOperationException("TODO!");
//        RunProfileState state = environment.getState();
//        if (state != null) {
//            doExecute(state, environment);
//        }
    }

    @Override
    public void execute(@Nonnull ExecutionEnvironment executionEnvironment, @Nullable Callback callback) throws ExecutionException {

    }
}
