// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.util.registry.Registry;
import consulo.application.Application;
import consulo.maven.rt.server.common.server.MavenArtifactEvent;
import consulo.maven.rt.server.common.server.MavenServerConsoleEvent;
import consulo.maven.rt.server.common.server.MavenServerConsoleIndicator;
import consulo.project.Project;
import consulo.util.lang.ExceptionUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.text.MessageFormat;
import java.util.*;

public abstract class MavenSyncConsoleBase implements MavenEventHandler {
    private static final String LINE_SEPARATOR = System.lineSeparator();

    private static final Map<Integer, String> LEVEL_TO_PREFIX = new HashMap<>();

    static {
        LEVEL_TO_PREFIX.put(MavenServerConsoleIndicator.LEVEL_DEBUG, "DEBUG");
        LEVEL_TO_PREFIX.put(MavenServerConsoleIndicator.LEVEL_INFO, "INFO");
        LEVEL_TO_PREFIX.put(MavenServerConsoleIndicator.LEVEL_WARN, "WARNING");
        LEVEL_TO_PREFIX.put(MavenServerConsoleIndicator.LEVEL_ERROR, "ERROR");
        LEVEL_TO_PREFIX.put(MavenServerConsoleIndicator.LEVEL_FATAL, "FATAL_ERROR");
    }

    private enum OutputType {
        NORMAL, ERROR
    }

    protected final Project myProject;
    private final ExternalSystemTaskId myTaskId;
    private final LinkedHashSet<Pair<ExternalSystemTaskId, String>> myStartedSet = new LinkedHashSet<>();
    private final SyncViewManager progressListener;
    private boolean hasErrors = false;

    protected MavenSyncConsoleBase(@Nonnull Project project) {
        this.myProject = project;
        this.myTaskId = createTaskId();
        this.progressListener = project.getInstance(SyncViewManager.class);
    }

    @Nonnull
    protected abstract String getTitle();

    @Nonnull
    protected abstract String getMessage();

    @Nonnull
    protected ExternalSystemTaskId createTaskId() {
        return ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myProject);
    }

    public void start() {
        DefaultBuildDescriptor descriptor = new DefaultBuildDescriptor(myTaskId, getTitle(), myProject.getBasePath(), System.currentTimeMillis());
        descriptor.setActivateToolWindowWhenFailed(true);
        descriptor.setActivateToolWindowWhenAdded(false);
        progressListener.onEvent(myTaskId, new StartBuildEventImpl(descriptor, getMessage()));
    }

    protected void startTask(@Nonnull ExternalSystemTaskId parentId, @Nonnull String taskName) {
        debugLog("Maven task: start " + taskName);
        if (myStartedSet.add(Pair.create(parentId, taskName))) {
            progressListener.onEvent(myTaskId, new StartEventImpl(taskName, parentId, System.currentTimeMillis(), taskName));
        }
    }

    protected void startTask(@Nonnull String taskName) {
        startTask(myTaskId, taskName);
    }

    protected void completeTask(@Nonnull ExternalSystemTaskId parentId, @Nonnull String taskName, @Nonnull EventResult result) {
        hasErrors = hasErrors || result instanceof FailureResultImpl;

        debugLog("Maven task: complete " + taskName + " with " + result);
        if (myStartedSet.remove(Pair.create(parentId, taskName))) {
            progressListener.onEvent(myTaskId, new FinishEventImpl(taskName, parentId, System.currentTimeMillis(), taskName, result));
        }
    }

    protected void completeTask(@Nonnull String taskName, @Nonnull EventResult result) {
        completeTask(myTaskId, taskName, result);
    }

    public void addError(@Nonnull String message) {
        String group = SyncBundle.message("build.event.title.error");
        progressListener.onEvent(myTaskId, new MessageEventImpl(myTaskId, MessageEvent.Kind.ERROR, group, message, message));
    }

    public void finish() {
        List<Pair<ExternalSystemTaskId, String>> tasks = new ArrayList<>(myStartedSet);
        Collections.reverse(tasks);
        debugLog("Tasks " + tasks + " are not completed! Force complete");
        for (Pair<ExternalSystemTaskId, String> task : tasks) {
            completeTask(task.getFirst(), task.getSecond(), new DerivedResultImpl());
        }
        progressListener.onEvent(myTaskId, new FinishBuildEventImpl(myTaskId, null, System.currentTimeMillis(), "",
            hasErrors ? new FailureResultImpl() : new DerivedResultImpl()));
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
        if (throwable != null || level == MavenServerConsoleIndicator.LEVEL_WARN
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

    private String composeLine(int level, String message) {
        return MessageFormat.format("[{0}] {1}", getPrefixByLevel(level), message);
    }

    private String getPrefixByLevel(int level) {
        return LEVEL_TO_PREFIX.get(level);
    }

    private void doPrint(@Nonnull String text, @Nonnull OutputType type) {
        boolean stdout = type == OutputType.NORMAL;
        if (StringUtil.isEmpty(text)) {
            return;
        }
        String toPrint = text.endsWith("\n") ? text : text + "\n";
        progressListener.onEvent(myTaskId, new OutputBuildEventImpl(myTaskId, toPrint, stdout));
    }

    private void debugLog(String s) {
        debugLog(s, null);
    }

    private void debugLog(String s, Throwable exception) {
        MavenLog.LOG.debug(s, exception);
    }
}
