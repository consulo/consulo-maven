// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import consulo.execution.ExecutionUtil;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.annotation.Nonnull;

public class MavenRebuildAction extends AnAction {
    private final ExecutionEnvironment myEnvironment;

    public MavenRebuildAction(@Nonnull ExecutionEnvironment environment) {
        myEnvironment = environment;
    }

    @Override
    @RequiredUIAccess
    public void update(@Nonnull AnActionEvent event) {
        event.getPresentation().setText(ExecutionLocalize.rerunConfigurationActionName(myEnvironment.getRunProfile().getName()));
        event.getPresentation().setIcon(PlatformIconGroup.actionsRestart());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent event) {
        ExecutionUtil.restart(myEnvironment);
    }
}
