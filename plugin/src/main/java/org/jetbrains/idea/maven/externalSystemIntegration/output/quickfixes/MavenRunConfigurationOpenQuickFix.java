// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import consulo.build.ui.issue.BuildIssueQuickFix;
import consulo.dataContext.DataContext;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.impl.internal.ui.RunDialog;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.project.MavenConfigurableBundle;

import java.util.concurrent.CompletableFuture;

public class MavenRunConfigurationOpenQuickFix implements BuildIssueQuickFix {
    private final RunnerAndConfigurationSettings runnerAndConfigurationSettings;

    public MavenRunConfigurationOpenQuickFix(@Nonnull RunnerAndConfigurationSettings runnerAndConfigurationSettings) {
        this.runnerAndConfigurationSettings = runnerAndConfigurationSettings;
    }

    @Nonnull
    @Override
    public String getId() {
        return "open_maven_run_configuration_open_quick_fix";
    }

    @Nonnull
    @Override
    public CompletableFuture<?> runQuickFix(@Nonnull Project project, @Nonnull DataContext dataContext) {
        RunDialog.editConfiguration(project, runnerAndConfigurationSettings,
            MavenConfigurableBundle.message("maven.settings.runner.vm.options"));
        return CompletableFuture.completedFuture(null);
    }
}
