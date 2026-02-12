// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.build.BuildProgressListener;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.FilePosition;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.*;
import com.intellij.build.events.impl.*;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.openapi.externalSystem.issue.BuildIssueException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.util.registry.Registry;
import consulo.application.Application;
import consulo.maven.rt.server.common.server.MavenArtifactEvent;
import consulo.maven.rt.server.common.server.MavenServerConsoleEvent;
import consulo.maven.rt.server.common.server.MavenServerConsoleIndicator;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.util.lang.ExceptionUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.idea.maven.buildtool.quickfix.MavenFullSyncQuickFix;
import org.jetbrains.idea.maven.buildtool.quickfix.OffMavenOfflineModeQuickFix;
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenSettingsQuickFix;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes.DownloadArtifactBuildIssue;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenSyncConsole implements MavenEventHandler, MavenBuildIssueHandler {
    public static final int EXIT_CODE_OK = 0;
    public static final int EXIT_CODE_SIGTERM = 143;

    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final Map<Integer, String> LEVEL_TO_PREFIX = new HashMap<>();
    private static final Set<String> JAVADOC_AND_SOURCE_CLASSIFIERS = Set.of("javadoc", "sources", "test-javadoc", "test-sources");
    private static final Pattern POSITION_FROM_DESCRIPTION_PATTERN = Pattern.compile("@(\\d+):(\\d+)");
    private static final Pattern POSITION_FROM_PATH_PATTERN = Pattern.compile(":(\\d+):(\\d+)");

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

    private final Project myProject;
    private final BuildProgressListener mySyncView;
    private ExternalSystemTaskId mySyncId;
    private boolean finished = false;
    private boolean started = false;
    private boolean syncTransactionStarted = false;
    private boolean hasErrors = false;
    private boolean hasUnresolved = false;
    private final Set<String> shownIssues = new HashSet<>();
    private final List<Runnable> myPostponed = new ArrayList<>();
    private LinkedHashSet<Pair<Object, String>> myStartedSet = new LinkedHashSet<>();

    public MavenSyncConsole(@Nonnull Project project) {
        this.myProject = project;
        this.mySyncView = project.getInstance(SyncViewManager.class);
        this.mySyncId = createTaskId();
    }

    public static class RescheduledMavenDownloadJobException extends CancellationException {
        public RescheduledMavenDownloadJobException(@Nullable String message) {
            super(message);
        }
    }

    public synchronized void startImport(boolean explicit) {
        if (started) {
            return;
        }
        started = true;
        finished = false;
        hasErrors = false;
        hasUnresolved = false;
        shownIssues.clear();
        mySyncId = createTaskId();

        DefaultBuildDescriptor descriptor = new DefaultBuildDescriptor(
            mySyncId,
            SyncBundle.message("maven.sync.title"),
            myProject.getBasePath(),
            System.currentTimeMillis()
        );
        descriptor.setActivateToolWindowWhenFailed(explicit);
        descriptor.setActivateToolWindowWhenAdded(false);
        descriptor.setNavigateToError(explicit ? ThreeState.YES : ThreeState.NO);

        mySyncView.onEvent(mySyncId, new StartBuildEventImpl(descriptor, SyncBundle.message("maven.sync.project.title", myProject.getName())));
        debugLog("maven sync: started importing " + myProject);

        for (Runnable action : myPostponed) {
            doIfImportInProcess(action);
        }
        myPostponed.clear();
    }

    @Nonnull
    private ExternalSystemTaskId createTaskId() {
        return ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myProject);
    }

    @Nonnull
    public ExternalSystemTaskId getTaskId() {
        return mySyncId;
    }

    public void addText(@Nonnull String text) {
        addText(text, true);
    }

    public synchronized void addText(@Nonnull String text, boolean stdout) {
        doIfImportInProcess(() -> addText(mySyncId, text, stdout));
    }

    public synchronized void addWrapperProgressText(@Nonnull String text) {
        doIfImportInProcess(() -> addText(SyncBundle.message("maven.sync.wrapper"), text, true));
    }

    private synchronized void addText(@Nonnull Object parentId, @Nonnull String text, boolean stdout) {
        doIfImportInProcess(() -> {
            if (StringUtil.isEmpty(text)) {
                return;
            }
            String toPrint = text.endsWith("\n") ? text : text + "\n";
            mySyncView.onEvent(mySyncId, new OutputBuildEventImpl(parentId, toPrint, stdout));
        });
    }

    public synchronized void addBuildEvent(@Nonnull BuildEvent buildEvent) {
        doIfImportInProcess(() -> {
            if (buildEvent instanceof BuildIssueEvent) {
                addBuildIssue(((BuildIssueEvent) buildEvent).getIssue(), ((BuildIssueEvent) buildEvent).getKind());
            }
            else {
                mySyncView.onEvent(mySyncId, buildEvent);
            }
        });
    }

    public synchronized void addWarning(@Nonnull String text, @Nonnull String description) {
        addWarning(text, description, null);
    }

    @Override
    public void addBuildIssue(@Nonnull BuildIssue issue, @Nonnull MessageEvent.Kind kind) {
        doIfImportInProcessOrPostpone(() -> {
            if (!newIssue(issue.getTitle() + issue.getDescription())) {
                return;
            }
            mySyncView.onEvent(mySyncId, new BuildIssueEventImpl(mySyncId, issue, kind));
            hasErrors = hasErrors || kind == MessageEvent.Kind.ERROR;
        });
    }

    public synchronized void addWarning(@Nonnull String text, @Nonnull String description, @Nullable FilePosition filePosition) {
        doIfImportInProcess(() -> {
            if (!newIssue(text + description + filePosition)) {
                return;
            }
            if (filePosition == null) {
                mySyncView.onEvent(mySyncId, new MessageEventImpl(
                    mySyncId,
                    MessageEvent.Kind.WARNING,
                    SyncBundle.message("maven.sync.group.compiler"),
                    text,
                    description
                ));
            }
            else {
                mySyncView.onEvent(mySyncId, new FileMessageEventImpl(
                    mySyncId,
                    MessageEvent.Kind.WARNING,
                    SyncBundle.message("maven.sync.group.compiler"),
                    text,
                    description,
                    filePosition
                ));
            }
        });
    }

    private boolean newIssue(@Nonnull String s) {
        return shownIssues.add(s);
    }

    public synchronized void finishImport() {
        finishImport(false);
    }

    public synchronized void finishImport(boolean showFullSyncQuickFix) {
        debugLog("Maven sync: finishImport");
        doFinish(showFullSyncQuickFix);
    }

    public void terminated(int exitCode) {
        doIfImportInProcess(() -> {
            if (EXIT_CODE_OK == exitCode || EXIT_CODE_SIGTERM == exitCode) {
                doFinish(false);
            }
            else {
                doTerminate(exitCode);
            }
        });
    }

    private void doTerminate(int exitCode) {
        if (syncTransactionStarted) {
            debugLog("Maven sync: sync transaction is still not finished, postpone build finish event");
            return;
        }
        List<Pair<Object, String>> tasks = new ArrayList<>(myStartedSet);
        Collections.reverse(tasks);
        debugLog("Tasks " + tasks + " are not completed! Force complete");
        for (Pair<Object, String> task : tasks) {
            completeTask(task.getFirst(), task.getSecond(),
                new FailureResultImpl(SyncBundle.message("maven.sync.failure.terminated", exitCode)));
        }

        mySyncView.onEvent(mySyncId, new FinishBuildEventImpl(
            mySyncId,
            null,
            System.currentTimeMillis(),
            "",
            new FailureResultImpl(SyncBundle.message("maven.sync.failure.terminated", exitCode))
        ));
        finished = true;
        started = false;
    }

    public synchronized void startWrapperResolving() {
        if (!started || finished) {
            startImport(true);
        }
        startTask(mySyncId, SyncBundle.message("maven.sync.wrapper"));
    }

    public synchronized void finishWrapperResolving() {
        finishWrapperResolving(null);
    }

    public synchronized void finishWrapperResolving(@Nullable Throwable e) {
        if (e != null) {
            addBuildIssue(new BuildIssue() {
                @Override
                @Nonnull
                public String getTitle() {
                    return SyncBundle.message("maven.sync.wrapper.failure");
                }

                @Override
                @Nonnull
                public String getDescription() {
                    return SyncBundle.message("maven.sync.wrapper.failure.description",
                        e.getLocalizedMessage(), OpenMavenSettingsQuickFix.ID);
                }

                @Override
                @Nonnull
                public List<BuildIssueQuickFix> getQuickFixes() {
                    return Collections.singletonList(new OpenMavenSettingsQuickFix());
                }

                @Override
                @Nullable
                public Navigatable getNavigatable(@Nonnull Project project) {
                    return null;
                }
            }, MessageEvent.Kind.WARNING);
        }
        completeTask(mySyncId, SyncBundle.message("maven.sync.wrapper"), new SuccessResultImpl());
    }

    public synchronized void notifyReadingProblems(@Nonnull VirtualFile file) {
        doIfImportInProcess(() -> {
            debugLog("reading problems in " + file);
            hasErrors = true;
            String desc = SyncBundle.message("maven.sync.failure.error.reading.file", file.getPath());
            mySyncView.onEvent(mySyncId, new FileMessageEventImpl(
                mySyncId,
                MessageEvent.Kind.ERROR,
                SyncBundle.message("maven.sync.group.error"),
                desc,
                desc,
                new FilePosition(new File(file.getPath()), -1, -1)
            ));
        });
    }

    public synchronized void notifyDownloadSourcesProblem(@Nonnull Exception e) {
        MessageEventImpl messageEvent;
        if (e instanceof RescheduledMavenDownloadJobException) {
            // a new job was submitted so no need to show anything to the user
            messageEvent = null;
        }
        else if (e instanceof CancellationException) {
            // a normal cancellation happened
            String message = MavenProjectBundle.message("maven.downloading.cancelled");
            messageEvent = new MessageEventImpl(mySyncId, MessageEvent.Kind.INFO,
                SyncBundle.message("build.event.title.error"), message, message);
        }
        else {
            hasErrors = true;
            messageEvent = MessageEventUtils.createMessageEvent(myProject, mySyncId, e);
        }
        if (messageEvent != null) {
            mySyncView.onEvent(mySyncId, messageEvent);
        }
    }

    public synchronized void showProblem(@Nonnull MavenProjectProblem problem) {
        doIfImportInProcess(() -> {
            hasErrors = hasErrors || problem.isError();
            String group = problem.isError()
                ? SyncBundle.message("maven.sync.group.error")
                : SyncBundle.message("maven.sync.group.warning");
            MessageEvent.Kind kind = problem.isError() ? MessageEvent.Kind.ERROR : MessageEvent.Kind.WARNING;
            FilePosition position = getFilePosition(problem);
            String message = problem.getDescription() != null
                ? problem.getDescription()
                : SyncBundle.message("maven.sync.failure.error.undefined.message");
            String detailedMessage = problem.getDescription() != null
                ? problem.getDescription()
                : SyncBundle.message("maven.sync.failure.error.undefined.detailed.message", problem.getPath());
            FileMessageEventImpl eventImpl = new FileMessageEventImpl(mySyncId, kind, group, message, detailedMessage, position);
            mySyncView.onEvent(mySyncId, eventImpl);
        });
    }

    @Nonnull
    private FilePosition getFilePosition(@Nonnull MavenProjectProblem problem) {
        int[] position = getPositionFromDescription(problem);
        if (position == null) {
            position = getPositionFromPath(problem);
        }
        int line = position != null ? position[0] : -1;
        int column = position != null ? position[1] : -1;

        String path = problem.getPath();
        if (line >= 0 && column >= 0) {
            String suffix = ":" + (line + 1) + ":" + column;
            int suffixIndex = path.lastIndexOf(suffix);
            if (suffixIndex > 0) {
                path = path.substring(0, suffixIndex);
            }
        }
        return new FilePosition(new File(path), line, column);
    }

    @Nullable
    private int[] getPositionFromDescription(@Nonnull MavenProjectProblem problem) {
        return getPosition(problem.getDescription(), problem, POSITION_FROM_DESCRIPTION_PATTERN);
    }

    @Nullable
    private int[] getPositionFromPath(@Nonnull MavenProjectProblem problem) {
        return getPosition(problem.getPath(), problem, POSITION_FROM_PATH_PATTERN);
    }

    @Nullable
    private int[] getPosition(@Nullable String source, @Nonnull MavenProjectProblem problem, @Nonnull Pattern pattern) {
        if (source == null) {
            return null;
        }
        if (problem.getType() == MavenProjectProblem.ProblemType.STRUCTURE) {
            Matcher matcher = pattern.matcher(source);
            int[] result = null;
            while (matcher.find()) {
                int line = Integer.parseInt(matcher.group(1)) - 1;
                int offset = Integer.parseInt(matcher.group(2));
                result = new int[]{line, offset};
            }
            return result;
        }
        return null;
    }

    @ApiStatus.Internal
    public synchronized void addException(@Nonnull Throwable e) {
        if (started && !finished) {
            MavenLog.LOG.warn(e);
            hasErrors = true;
            BuildIssueException buildIssueException = ExceptionUtil.findCause(e, BuildIssueException.class);
            if (buildIssueException != null) {
                addBuildIssue(buildIssueException.getBuildIssue(), MessageEvent.Kind.ERROR);
            }
            else {
                mySyncView.onEvent(mySyncId, MessageEventUtils.createMessageEvent(myProject, mySyncId, e));
            }
        }
        else {
            this.startImport(true);
            this.addException(e);
            this.finishImport();
        }
    }

    @Nonnull
    private String getKeyPrefix(@Nonnull MavenServerConsoleIndicator.ResolveType type) {
        switch (type) {
            case PLUGIN:
                return "maven.sync.plugins";
            case DEPENDENCY:
                return "maven.sync.dependencies";
            default:
                return "maven.sync.dependencies";
        }
    }

    private synchronized void doFinish(boolean showFullSyncQuickFix) {
        if (syncTransactionStarted) {
            debugLog("Maven sync: sync transaction is still not finished, postpone build finish event");
            return;
        }
        List<Pair<Object, String>> tasks = new ArrayList<>(myStartedSet);
        Collections.reverse(tasks);
        debugLog("Tasks " + tasks + " are not completed! Force complete");
        for (Pair<Object, String> task : tasks) {
            completeTask(task.getFirst(), task.getSecond(), new DerivedResultImpl());
        }
        mySyncView.onEvent(mySyncId, new FinishBuildEventImpl(
            mySyncId,
            null,
            System.currentTimeMillis(),
            "",
            hasErrors ? new FailureResultImpl() : new DerivedResultImpl()
        ));

        attachOfflineQuickFix();
        if (showFullSyncQuickFix) {
            attachFullSyncQuickFix();
        }
        finished = true;
        started = false;
    }

    private void attachFullSyncQuickFix() {
        try {
            mySyncView.onEvent(mySyncId, new BuildIssueEventImpl(mySyncId, new BuildIssue() {
                @Override
                @Nonnull
                public String getTitle() {
                    return "Sync Finished";
                }

                @Override
                @Nonnull
                public String getDescription() {
                    return "Sync finished. If there is something wrong with the project model, <a href=\"" +
                        MavenFullSyncQuickFix.ID + "\">reload all projects</a>\n";
                }

                @Override
                @Nonnull
                public List<BuildIssueQuickFix> getQuickFixes() {
                    return Collections.singletonList(new MavenFullSyncQuickFix());
                }

                @Override
                @Nullable
                public Navigatable getNavigatable(@Nonnull Project project) {
                    return null;
                }
            }, MessageEvent.Kind.INFO));
        }
        catch (Exception ignored) {
        }
    }

    private void attachOfflineQuickFix() {
        try {
            MavenGeneralSettings generalSettings = MavenWorkspaceSettingsComponent.getInstance(myProject)
                .getSettings().getGeneralSettings();
            if (hasUnresolved && generalSettings.isWorkOffline()) {
                mySyncView.onEvent(mySyncId, new BuildIssueEventImpl(mySyncId, new BuildIssue() {
                    @Override
                    @Nonnull
                    public String getTitle() {
                        return "Dependency Resolution Failed";
                    }

                    @Override
                    @Nonnull
                    public String getDescription() {
                        return "<a href=\"" + OffMavenOfflineModeQuickFix.ID + "\">Switch Off Offline Mode</a>\n";
                    }

                    @Override
                    @Nonnull
                    public List<BuildIssueQuickFix> getQuickFixes() {
                        return Collections.singletonList(new OffMavenOfflineModeQuickFix());
                    }

                    @Override
                    @Nullable
                    public Navigatable getNavigatable(@Nonnull Project project) {
                        return null;
                    }
                }, MessageEvent.Kind.ERROR));
            }
        }
        catch (Exception ignored) {
        }
    }

    private synchronized void showArtifactBuildIssue(@Nonnull String keyPrefix,
                                                     @Nonnull String dependency,
                                                     @Nullable String errorMessage) {
        doIfImportInProcess(() -> {
            hasErrors = true;
            hasUnresolved = true;
            String umbrellaString = SyncBundle.message(keyPrefix + ".resolve");
            String errorString = SyncBundle.message(keyPrefix + ".resolve.error", dependency);
            startTask(mySyncId, umbrellaString);
            BuildIssue buildIssue = DownloadArtifactBuildIssue.getIssue(errorString, errorMessage != null ? errorMessage : errorString);
            mySyncView.onEvent(mySyncId, new BuildIssueEventImpl(umbrellaString, buildIssue, MessageEvent.Kind.ERROR));
            addText(mySyncId, errorString, false);
        });
    }

    public void showArtifactBuildIssue(@Nonnull MavenServerConsoleIndicator.ResolveType type,
                                       @Nonnull String dependency,
                                       @Nullable String errorMessage) {
        showArtifactBuildIssue(getKeyPrefix(type), dependency, errorMessage);
    }

    public synchronized void showBuildIssue(@Nonnull BuildIssue buildIssue) {
        doIfImportInProcess(() -> {
            hasErrors = true;
            hasUnresolved = true;
            String key = getKeyPrefix(MavenServerConsoleIndicator.ResolveType.DEPENDENCY);
            startTask(mySyncId, key);
            mySyncView.onEvent(mySyncId, new BuildIssueEventImpl(key, buildIssue, MessageEvent.Kind.ERROR));
        });
    }

    public synchronized void showBuildIssue(@Nonnull BuildIssue buildIssue, @Nonnull MessageEvent.Kind kind) {
        doIfImportInProcess(() -> {
            hasErrors = hasErrors || kind == MessageEvent.Kind.ERROR;
            String key = getKeyPrefix(MavenServerConsoleIndicator.ResolveType.DEPENDENCY);
            startTask(mySyncId, key);
            mySyncView.onEvent(mySyncId, new BuildIssueEventImpl(key, buildIssue, kind));
        });
    }

    private synchronized void startTask(@Nonnull Object parentId, @Nonnull String taskName) {
        doIfImportInProcess(() -> {
            debugLog("Maven sync: start " + taskName);
            if (myStartedSet.add(Pair.create(parentId, taskName))) {
                mySyncView.onEvent(mySyncId, new StartEventImpl(taskName, parentId, System.currentTimeMillis(), taskName));
            }
        });
    }

    private synchronized void completeTask(@Nonnull Object parentId, @Nonnull String taskName, @Nonnull EventResult result) {
        doIfImportInProcess(() -> {
            hasErrors = hasErrors || result instanceof FailureResultImpl;

            debugLog("Maven sync: complete " + taskName + " with " + result);
            if (myStartedSet.remove(Pair.create(parentId, taskName))) {
                mySyncView.onEvent(mySyncId, new FinishEventImpl(taskName, parentId, System.currentTimeMillis(), taskName, result));
            }
        });
    }

    public void finishPluginResolution() {
        completeUmbrellaEvents(getKeyPrefix(MavenServerConsoleIndicator.ResolveType.PLUGIN));
    }

    public void finishArtifactsDownload() {
        completeUmbrellaEvents(getKeyPrefix(MavenServerConsoleIndicator.ResolveType.DEPENDENCY));
    }

    private synchronized void completeUmbrellaEvents(@Nonnull String keyPrefix) {
        doIfImportInProcess(() -> {
            String taskName = SyncBundle.message(keyPrefix + ".resolve");
            completeTask(mySyncId, taskName, new DerivedResultImpl());
        });
    }

    private synchronized void downloadEventStarted(@Nonnull String keyPrefix, @Nonnull String dependency) {
        doIfImportInProcess(() -> {
            String downloadString = SyncBundle.message(keyPrefix + ".download");
            String downloadArtifactString = SyncBundle.message(keyPrefix + ".artifact.download", dependency);
            startTask(mySyncId, downloadString);
            startTask(downloadString, downloadArtifactString);
        });
    }

    private synchronized void downloadEventCompleted(@Nonnull String keyPrefix, @Nonnull String dependency) {
        doIfImportInProcess(() -> {
            String downloadString = SyncBundle.message(keyPrefix + ".download");
            String downloadArtifactString = SyncBundle.message(keyPrefix + ".artifact.download", dependency);
            addText(downloadArtifactString, downloadArtifactString, true);
            completeTask(downloadString, downloadArtifactString, new SuccessResultImpl(false));
        });
    }

    private synchronized void downloadEventFailed(@Nonnull String keyPrefix,
                                                  @Nonnull String dependency,
                                                  @Nonnull String error,
                                                  @Nullable String stackTrace) {
        doIfImportInProcess(() -> {
            String downloadString = SyncBundle.message(keyPrefix + ".download");
            String downloadArtifactString = SyncBundle.message(keyPrefix + ".artifact.download", dependency);

            if (isJavadocOrSource(dependency)) {
                addText(downloadArtifactString, SyncBundle.message("maven.sync.failure.dependency.not.found", dependency), true);
                completeTask(downloadString, downloadArtifactString, new MessageEventResult() {
                    @Override
                    public MessageEvent.Kind getKind() {
                        return MessageEvent.Kind.WARNING;
                    }

                    @Override
                    @Nullable
                    public String getDetails() {
                        return SyncBundle.message("maven.sync.failure.dependency.not.found", dependency);
                    }
                });
            }
            else {
                if (stackTrace != null && Registry.is("maven.spy.events.debug")) {
                    addText(downloadArtifactString, stackTrace, false);
                }
                else {
                    addText(downloadArtifactString, error, true);
                }
                completeTask(downloadString, downloadArtifactString, new FailureResultImpl(error));
            }
        });
    }

    private boolean isJavadocOrSource(@Nonnull String dependency) {
        String[] split = dependency.split(":");
        if (split.length < 4) {
            return false;
        }
        String classifier = split[2];
        return JAVADOC_AND_SOURCE_CLASSIFIERS.contains(classifier);
    }

    private void doIfImportInProcess(@Nonnull Runnable action) {
        if (!started || finished) {
            return;
        }
        action.run();
    }

    private void doIfImportInProcessOrPostpone(@Nonnull Runnable action) {
        if (!started || finished) {
            myPostponed.add(action);
        }
        else {
            action.run();
        }
    }

    @ApiStatus.Experimental
    public synchronized void startTransaction() {
        syncTransactionStarted = true;
    }

    @ApiStatus.Experimental
    public synchronized void finishTransaction(boolean showFullSyncQuickFix) {
        syncTransactionStarted = false;
        finishImport(showFullSyncQuickFix);
    }

    @Override
    public synchronized void handleDownloadEvents(@Nonnull List<MavenArtifactEvent> downloadEvents) {
        for (MavenArtifactEvent e : downloadEvents) {
            String key = getKeyPrefix(e.getResolveType());
            String id = e.getDependencyId();
            switch (e.getArtifactEventType()) {
                case DOWNLOAD_STARTED:
                    downloadEventStarted(key, id);
                    break;
                case DOWNLOAD_COMPLETED:
                    downloadEventCompleted(key, id);
                    break;
                case DOWNLOAD_FAILED:
                    downloadEventFailed(key, id, e.getErrorMessage(), e.getStackTrace());
                    break;
            }
        }
    }

    @Override
    public void handleConsoleEvents(@Nonnull List<MavenServerConsoleEvent> consoleEvents) {
        for (MavenServerConsoleEvent e : consoleEvents) {
            printMessage(e.getLevel(), e.getMessage(), e.getThrowable());
        }
    }

    private void printMessage(int level, @Nonnull String string, @Nullable Throwable throwable) {
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

    private void doPrint(@Nonnull String text, @Nonnull OutputType type) {
        addText(text, type == OutputType.NORMAL);
    }

    private boolean isSuppressed(int level) {
        return level < MavenProjectsManager.getInstance(myProject).getGeneralSettings().getOutputLevel().getLevel();
    }

    @Nonnull
    private String composeLine(int level, @Nullable String message) {
        return MessageFormat.format("[{0}] {1}", getPrefixByLevel(level), message);
    }

    @Nullable
    private String getPrefixByLevel(int level) {
        return LEVEL_TO_PREFIX.get(level);
    }

    private static void debugLog(@Nonnull String s) {
        debugLog(s, null);
    }

    private static void debugLog(@Nonnull String s, @Nullable Throwable exception) {
        MavenLog.LOG.debug(s, exception);
    }
}
