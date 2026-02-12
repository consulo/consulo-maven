// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.impl.RunDialog;
import consulo.project.Project;
import consulo.ui.ex.action.DataContext;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;

import java.util.concurrent.CompletableFuture;

public class OpenRunConfigurationQuickFix implements BuildIssueQuickFix {
    public static final String ID = "open_run_configuration_quick_fix";

    private final MavenRunConfiguration myRunConfiguration;

    public OpenRunConfigurationQuickFix(@Nonnull MavenRunConfiguration runConfiguration) {
        this.myRunConfiguration = runConfiguration;
    }

    @Nonnull
    @Override
    public String getId() {
        return ID;
    }

    @Nonnull
    @Override
    public CompletableFuture<?> runQuickFix(@Nonnull Project project, @Nonnull DataContext dataContext) {
        RunManager runManager = RunManager.getInstance(project);
        if (myRunConfiguration.getFactory() == null) {
            return CompletableFuture.completedFuture(null);
        }

        var settings = runManager.createConfiguration(myRunConfiguration, myRunConfiguration.getFactory());
        runManager.setSelectedConfiguration(settings);
        RunDialog.editConfiguration(myRunConfiguration.getProject(), settings,
            ExecutionBundle.message("create.run.configuration.for.item.dialog.title", settings.getName()));
        return CompletableFuture.completedFuture(null);
    }
}
