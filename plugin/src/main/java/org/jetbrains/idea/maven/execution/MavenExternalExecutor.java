/* ==========================================================================
 * Copyright 2006 Mevenide Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */


package org.jetbrains.idea.maven.execution;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.maven.rt.server.common.server.MavenServerConsole;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MavenExternalExecutor extends MavenExecutor {
    private ProcessHandler myProcessHandler;

    private static final String PHASE_INFO_REGEXP = "\\[INFO\\] \\[.*:.*\\]";
    private static final int INFO_PREFIX_SIZE = "[INFO] ".length();

    private OwnJavaParameters myJavaParameters;
    private ExecutionException myParameterCreationError;

    @RequiredReadAction
    public MavenExternalExecutor(
        Project project,
        @Nonnull MavenRunnerParameters parameters,
        @Nullable MavenGeneralSettings coreSettings,
        @Nullable MavenRunnerSettings runnerSettings,
        @Nonnull MavenConsole console
    ) {
        super(parameters, MavenRunnerLocalize.externalExecutorCaption().get(), console);

        try {
            myJavaParameters = MavenExternalParameters.createJavaParameters(project, myParameters, coreSettings, runnerSettings);
        }
        catch (ExecutionException e) {
            myParameterCreationError = e;
        }
    }

    @Override
    public boolean execute(final ProgressIndicator indicator) {
        displayProgress();

        try {
            if (myParameterCreationError != null) {
                throw myParameterCreationError;
            }

            myProcessHandler = ProcessHandlerBuilder.create(myJavaParameters.toCommandLine()).build();
            myProcessHandler.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(ProcessEvent event, Key outputType) {
                    updateProgress(indicator, event.getText());
                }
            });
            myConsole.attachToProcess(myProcessHandler);
        }
        catch (ExecutionException e) {
            myConsole.systemMessage(
                MavenServerConsole.LEVEL_FATAL,
                MavenRunnerLocalize.externalStartupFailed(e.getMessage()).get(),
                null
            );
            return false;
        }

        start();
        readProcessOutput();
        stop();

        return printExitSummary();
    }

    @Override
    void stop() {
        if (myProcessHandler != null) {
            myProcessHandler.destroyProcess();
            myProcessHandler.waitFor();
            setExitCode(myProcessHandler.getExitCode());
        }
        super.stop();
    }

    private void readProcessOutput() {
        myProcessHandler.startNotify();
        myProcessHandler.waitFor();
    }

    private void updateProgress(@Nullable final ProgressIndicator indicator, final String text) {
        if (indicator != null) {
            if (indicator.isCanceled() && !isCancelled()) {
                Application.get().invokeLater(this::cancel);
            }
            if (text.matches(PHASE_INFO_REGEXP)) {
                indicator.setText2(text.substring(INFO_PREFIX_SIZE));
            }
        }
    }
}
