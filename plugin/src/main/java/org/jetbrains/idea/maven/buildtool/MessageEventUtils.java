// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import consulo.application.Application;
import consulo.build.ui.event.BuildEventFactory;
import consulo.build.ui.event.MessageEvent;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationGroup;
import consulo.util.lang.ExceptionUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenSyncLocalize;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.server.CannotStartServerException;

final class MessageEventUtils {
    private MessageEventUtils() {
    }

    @Nonnull
    static MessageEvent createMessageEvent(@Nonnull Project project,
                                           @Nonnull ExternalSystemTaskId taskId,
                                           @Nonnull Throwable e) {
        Throwable error = e;
        NotificationGroup group = MavenBuildNotification.BUILD_ERROR;

        CannotStartServerException csse = ExceptionUtil.findCause(e, CannotStartServerException.class);
        if (csse != null) {
            group = MavenBuildNotification.BUILD_INTERNAL_ERROR;
            ExecutionException executionException = ExceptionUtil.findCause(csse, ExecutionException.class);
            error = executionException != null ? executionException : csse;
        }

        String message = getExceptionText(project, error);
        return Application.get().getInstance(BuildEventFactory.class).createMessageEvent(taskId, MessageEvent.Kind.ERROR, group, message, message);
    }

    @Nonnull
    private static String getExceptionText(@Nonnull Project project, @Nonnull Throwable e) {
        MavenGeneralSettings generalSettings = MavenWorkspaceSettingsComponent.getInstance(project).getSettings().generalSettings;

        if (generalSettings != null && generalSettings.isPrintErrorStackTraces()) {
            return ExceptionUtil.getThrowableText(e);
        }

        String localizedMessage = e.getLocalizedMessage();
        if (localizedMessage != null && !localizedMessage.isEmpty()) {
            return localizedMessage;
        }

        String message = e.getMessage();
        if (StringUtil.isEmpty(message)) {
            return MavenSyncLocalize.buildEventTitleError().get();
        }
        return message;
    }
}
