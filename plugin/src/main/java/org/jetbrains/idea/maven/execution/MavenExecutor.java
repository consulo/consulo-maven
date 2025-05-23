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

import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;

import jakarta.annotation.Nullable;

import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.jetbrains.idea.maven.project.MavenConsole;
import consulo.maven.rt.server.common.server.MavenServerConsole;

import java.text.MessageFormat;

public abstract class MavenExecutor {
    final MavenRunnerParameters myParameters;
    private final String myCaption;
    protected MavenConsole myConsole;
    private String myAction;

    private boolean stopped = true;
    private boolean cancelled = false;
    private int exitCode = 0;

    public MavenExecutor(MavenRunnerParameters parameters, String caption, MavenConsole console) {
        myParameters = parameters;
        myCaption = caption;
        myConsole = console;
    }

    public String getCaption() {
        return myCaption;
    }

    public MavenConsole getConsole() {
        return myConsole;
    }

    public void setAction(@Nullable final String action) {
        myAction = action;
    }

    public boolean isStopped() {
        return stopped;
    }

    void start() {
        stopped = false;
    }

    void stop() {
        stopped = true;
        myConsole.setOutputPaused(false);
    }

    boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
        stop();
    }

    protected void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    void displayProgress() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
            indicator.setText(MessageFormat.format(
                "{0} {1}",
                myAction != null ? myAction : MavenRunnerLocalize.mavenRunning().get(),
                myParameters.getWorkingDirPath()
            ));
            indicator.setText2(myParameters.getGoals().toString());
        }
    }

    protected boolean printExitSummary() {
        if (isCancelled()) {
            myConsole.systemMessage(MavenServerConsole.LEVEL_INFO, MavenRunnerLocalize.mavenExecutionAborted().get(), null);
            return false;
        }
        else if (exitCode == 0) {
            myConsole.systemMessage(MavenServerConsole.LEVEL_INFO, MavenRunnerLocalize.mavenExecutionFinished().get(), null);
            return true;
        }
        else {
            myConsole.systemMessage(
                MavenServerConsole.LEVEL_ERROR,
                MavenRunnerLocalize.mavenExecutionTerminatedAbnormally(exitCode).get(),
                null
            );
            return false;
        }
    }

    public abstract boolean execute(@Nullable ProgressIndicator indicator);
}
