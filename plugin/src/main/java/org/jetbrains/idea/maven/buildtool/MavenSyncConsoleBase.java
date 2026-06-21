// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import consulo.application.Application;
import consulo.application.util.registry.Registry;
import consulo.build.ui.DefaultBuildDescriptor;
import consulo.build.ui.SyncViewManager;
import consulo.build.ui.event.BuildEventFactory;
import consulo.build.ui.event.EventResult;
import consulo.build.ui.event.FailureResult;
import consulo.build.ui.event.MessageEvent;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.externalSystem.model.task.ExternalSystemTaskType;
import consulo.localize.LocalizeValue;
import consulo.maven.rt.server.common.server.MavenArtifactEvent;
import consulo.maven.rt.server.common.server.MavenServerConsoleEvent;
import consulo.maven.rt.server.common.server.MavenServerConsoleIndicator;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.util.lang.ExceptionUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.text.MessageFormat;
import java.util.*;

public abstract class MavenSyncConsoleBase implements MavenEventHandler {
    private static final LocalizeValue LINE_SEPARATOR = LocalizeValue.of(Platform.current().os().lineSeparator().getSeparatorString());

    private static final Map<Integer, String> LEVEL_TO_PREFIX = Map.of(
        MavenServerConsoleIndicator.LEVEL_DEBUG, "DEBUG",
        MavenServerConsoleIndicator.LEVEL_INFO, "INFO",
        MavenServerConsoleIndicator.LEVEL_WARN, "WARNING",
        MavenServerConsoleIndicator.LEVEL_ERROR, "ERROR",
        MavenServerConsoleIndicator.LEVEL_FATAL, "FATAL_ERROR"
    );

    private enum OutputType {
        NORMAL,
        ERROR
    }

    private final Project myProject;
    private final ExternalSystemTaskId myTaskId;
    private final SequencedSet<Pair<ExternalSystemTaskId, LocalizeValue>> myStartedSet = new LinkedHashSet<>();
    private final SyncViewManager progressListener;
    private boolean hasErrors = false;
    private final BuildEventFactory myFactory;

    protected MavenSyncConsoleBase(@Nonnull Project project) {
        myProject = project;
        myTaskId = createTaskId();
        progressListener = project.getInstance(SyncViewManager.class);
        myFactory = project.getApplication().getInstance(BuildEventFactory.class);
    }

    @Nonnull
    protected abstract LocalizeValue getTitle();

    @Nonnull
    protected abstract LocalizeValue getMessage();

    @Nonnull
    protected ExternalSystemTaskId createTaskId() {
        return ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myProject);
    }

    public void start() {
        DefaultBuildDescriptor descriptor = new DefaultBuildDescriptor(
            myTaskId,
            getTitle(),
            myProject.getBasePath(),
            System.currentTimeMillis()
        );
        descriptor.setActivateToolWindowWhenFailed(true);
        descriptor.setActivateToolWindowWhenAdded(false);
        progressListener.onEvent(myTaskId, myFactory.createStartBuildEvent(descriptor, getMessage()));
    }

    protected void startTask(@Nonnull ExternalSystemTaskId parentId, @Nonnull LocalizeValue taskName) {
        debugLog("Maven task: start " + taskName);
        if (myStartedSet.add(Pair.create(parentId, taskName))) {
            progressListener.onEvent(myTaskId, myFactory.createStartEvent(taskName, parentId, System.currentTimeMillis(), taskName));
        }
    }

    protected void startTask(@Nonnull LocalizeValue taskName) {
        startTask(myTaskId, taskName);
    }

    protected void completeTask(@Nonnull ExternalSystemTaskId parentId, @Nonnull LocalizeValue taskName, @Nonnull EventResult result) {
        hasErrors = hasErrors || result instanceof FailureResult;

        debugLog("Maven task: complete " + taskName + " with " + result);
        if (myStartedSet.remove(Pair.create(parentId, taskName))) {
            progressListener.onEvent(
                myTaskId,
                myFactory.createFinishBuildEvent(taskName, parentId, System.currentTimeMillis(), taskName, result)
            );
        }
    }

    protected void completeTask(@Nonnull LocalizeValue taskName, @Nonnull EventResult result) {
        completeTask(myTaskId, taskName, result);
    }

    public void addError(@Nonnull LocalizeValue message) {
        progressListener.onEvent(
            myTaskId,
            myFactory.createMessageEvent(myTaskId, MessageEvent.Kind.ERROR, MavenBuildNotification.BUILD_ERROR, message, message)
        );
    }

    public void finish() {
        List<Pair<ExternalSystemTaskId, LocalizeValue>> tasks = new ArrayList<>(myStartedSet);
        Collections.reverse(tasks);
        debugLog("Tasks " + tasks + " are not completed! Force complete");
        for (Pair<ExternalSystemTaskId, LocalizeValue> task : tasks) {
            completeTask(task.getFirst(), task.getSecond(), myFactory.createDerivedResult());
        }
        progressListener.onEvent(
            myTaskId,
            myFactory.createFinishBuildEvent(myTaskId,
                null,
                System.currentTimeMillis(),
                LocalizeValue.empty(),
                hasErrors ? myFactory.newFailure().createResult() : myFactory.createDerivedResult()
            )
        );
    }

    @Override
    public void handleDownloadEvents(@Nonnull List<MavenArtifactEvent> downloadEvents) {
        // TODO: show in UI?
        MavenLogEventHandler.INSTANCE.handleDownloadEvents(downloadEvents);
    }

    @Override
    public void handleConsoleEvents(@Nonnull List<MavenServerConsoleEvent> consoleEvents) {
        for (MavenServerConsoleEvent e : consoleEvents) {
            printMessage(e.getLevel(), e.getMessage(), e.getThrowable());
        }
    }

    private void printMessage(int level, String string, Throwable throwable) {
        if (isSuppressed(level)) {
            return;
        }

        OutputType type = OutputType.NORMAL;
        if (throwable != null
            || level == MavenServerConsoleIndicator.LEVEL_WARN
            || level == MavenServerConsoleIndicator.LEVEL_ERROR
            || level == MavenServerConsoleIndicator.LEVEL_FATAL) {
            type = OutputType.ERROR;
        }

        doPrint(composeLine(level, string), type);

        if (throwable != null) {
            String throwableText = ExceptionUtil.getThrowableText(throwable);
            if (Registry.is("maven.print.import.stacktraces") || Application.get().isUnitTestMode()) {
                doPrint(LINE_SEPARATOR + composeLine(MavenServerConsoleIndicator.LEVEL_ERROR, throwableText), type);
            }
            else {
                doPrint(LINE_SEPARATOR + composeLine(MavenServerConsoleIndicator.LEVEL_ERROR, throwable.getMessage()), type);
            }
        }
    }

    private boolean isSuppressed(int level) {
        return level < MavenProjectsManager.getInstance(myProject).getGeneralSettings().getOutputLevel().getLevel();
    }

    @Nonnull
    private String composeLine(int level, @Nullable String message) {
        return MessageFormat.format("[{0}] {1}", LEVEL_TO_PREFIX.getOrDefault(level, "???"), message);
    }

    private void doPrint(@Nonnull String text, @Nonnull OutputType type) {
        boolean stdout = type == OutputType.NORMAL;
        if (StringUtil.isEmpty(text)) {
            return;
        }
        String toPrint = text.endsWith("\n") ? text : text + "\n";
        progressListener.onEvent(myTaskId, myFactory.createOutputBuildEvent(myTaskId, toPrint, stdout));
    }

    private static void debugLog(@Nonnull String s) {
        debugLog(s, null);
    }

    private static void debugLog(@Nonnull String s, @Nullable Throwable exception) {
        MavenLog.LOG.debug(s, exception);
    }
}
