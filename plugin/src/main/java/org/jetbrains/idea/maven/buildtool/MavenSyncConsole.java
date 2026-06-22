// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import consulo.application.Application;
import consulo.application.util.registry.Registry;
import consulo.build.ui.DefaultBuildDescriptor;
import consulo.build.ui.FilePosition;
import consulo.build.ui.SyncViewManager;
import consulo.build.ui.event.*;
import consulo.build.ui.issue.BuildIssue;
import consulo.build.ui.issue.BuildIssueQuickFix;
import consulo.build.ui.progress.BuildProgressListener;
import consulo.dataContext.DataProvider;
import consulo.externalSystem.issue.BuildIssueException;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.externalSystem.model.task.ExternalSystemTaskType;
import consulo.localize.LocalizeValue;
import consulo.maven.rt.server.common.model.MavenProjectProblem;
import consulo.maven.rt.server.common.server.MavenArtifactEvent;
import consulo.maven.rt.server.common.server.MavenServerConsoleEvent;
import consulo.maven.rt.server.common.server.MavenServerConsoleIndicator;
import consulo.navigation.Navigatable;
import consulo.platform.Platform;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationGroup;
import consulo.util.lang.ExceptionUtil;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.localize.MavenSyncLocalize;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenSyncConsole implements MavenEventHandler, MavenBuildIssueHandler {
    enum ResolveDescriptor {
        DEPENDENCY {
            @Override
            public LocalizeValue getTaskName() {
                return MavenSyncLocalize.mavenSyncDependenciesResolve();
            }

            @Override
            public LocalizeValue getDownloadingMessage() {
                return MavenSyncLocalize.mavenSyncDependenciesDownload();
            }

            @Override
            public LocalizeValue getErrorMessage(String dependency) {
                return MavenSyncLocalize.mavenSyncDependenciesResolveError(dependency);
            }

            @Override
            public LocalizeValue getDownloadingMessage(String dependency) {
                return MavenSyncLocalize.mavenSyncDependenciesArtifactDownload(dependency);
            }
        },
        PLUGIN {
            @Override
            public LocalizeValue getTaskName() {
                return MavenSyncLocalize.mavenSyncPluginsResolve();
            }

            @Override
            public LocalizeValue getDownloadingMessage() {
                return MavenSyncLocalize.mavenSyncPluginsDownload();
            }

            @Override
            public LocalizeValue getErrorMessage(String dependency) {
                return MavenSyncLocalize.mavenSyncPluginsResolveError(dependency);
            }

            @Override
            public LocalizeValue getDownloadingMessage(String dependency) {
                return MavenSyncLocalize.mavenSyncPluginsArtifactDownload(dependency);
            }
        };

        abstract public LocalizeValue getTaskName();

        abstract public LocalizeValue getDownloadingMessage();

        abstract public LocalizeValue getDownloadingMessage(String dependency);

        abstract public LocalizeValue getErrorMessage(String dependency);

        public static ResolveDescriptor of(MavenServerConsoleIndicator.ResolveType type) {
            return switch (type) {
                case DEPENDENCY -> DEPENDENCY;
                case PLUGIN -> PLUGIN;
            };
        }
    }

    public static final int EXIT_CODE_OK = 0;
    public static final int EXIT_CODE_SIGTERM = 143;

    private static final LocalizeValue LINE_SEPARATOR = LocalizeValue.of(Platform.current().os().lineSeparator().getSeparatorString());
    private static final Map<Integer, String> LEVEL_TO_PREFIX = Map.of(
        MavenServerConsoleIndicator.LEVEL_DEBUG, "DEBUG",
        MavenServerConsoleIndicator.LEVEL_INFO, "INFO",
        MavenServerConsoleIndicator.LEVEL_WARN, "WARNING",
        MavenServerConsoleIndicator.LEVEL_ERROR, "ERROR",
        MavenServerConsoleIndicator.LEVEL_FATAL, "FATAL_ERROR"
    );

    private static final Set<String> JAVADOC_AND_SOURCE_CLASSIFIERS = Set.of("javadoc", "sources", "test-javadoc", "test-sources");
    private static final Pattern POSITION_FROM_DESCRIPTION_PATTERN = Pattern.compile("@(\\d+):(\\d+)");
    private static final Pattern POSITION_FROM_PATH_PATTERN = Pattern.compile(":(\\d+):(\\d+)");

    private final Project myProject;
    private final BuildProgressListener mySyncView;
    private ExternalSystemTaskId mySyncId;
    private boolean finished = false;
    private boolean started = false;
    private boolean syncTransactionStarted = false;
    private boolean hasErrors = false;
    private boolean hasUnresolved = false;
    private final Set<LocalizeValue> shownIssues = new HashSet<>();
    private final List<Runnable> myPostponed = new ArrayList<>();
    private LinkedHashSet<Pair<Object, LocalizeValue>> myStartedSet = new LinkedHashSet<>();
    private final BuildEventFactory myFactory;

    public MavenSyncConsole(@Nonnull Project project) {
        myProject = project;
        mySyncView = project.getInstance(SyncViewManager.class);
        mySyncId = createTaskId();
        myFactory = project.getApplication().getInstance(BuildEventFactory.class);
    }

    public boolean canPause() {
        return false;
    }

    public boolean isOutputPaused() {
        return false;
    }

    public void setOutputPaused(boolean outputPaused) {
    }

    public void attachToProcess(ProcessHandler processHandler) {
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
            MavenSyncLocalize.mavenSyncTitle().get(),
            myProject.getBasePath(),
            System.currentTimeMillis()
        );
        descriptor.setActivateToolWindowWhenFailed(explicit);
        descriptor.setActivateToolWindowWhenAdded(false);
        // TODO ! descriptor.setNavigateToError(explicit ? ThreeState.YES : ThreeState.NO);

        mySyncView.onEvent(
            mySyncId,
            myFactory.createStartBuildEvent(descriptor, MavenSyncLocalize.mavenSyncProjectTitle(myProject.getName()).get())
        );
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

    public void addText(@Nonnull LocalizeValue text) {
        addText(text, true);
    }

    public synchronized void addText(@Nonnull LocalizeValue text, boolean stdout) {
        doIfImportInProcess(() -> addText(mySyncId, text, stdout));
    }

    public synchronized void addWrapperProgressText(@Nonnull LocalizeValue text) {
        doIfImportInProcess(() -> addText(MavenSyncLocalize.mavenSyncWrapper(), text, true));
    }

    private synchronized void addText(@Nonnull Object parentId, @Nonnull LocalizeValue text, boolean stdout) {
        doIfImportInProcess(() -> {
            if (text.isEmpty()) {
                return;
            }
            mySyncView.onEvent(
                mySyncId,
                myFactory.createOutputBuildEvent(parentId, text.map(str -> str.endsWith("\n") ? str : str + "\n").get(), stdout)
            );
        });
    }

    public synchronized void addBuildEvent(@Nonnull BuildEvent buildEvent) {
        doIfImportInProcess(() -> {
            if (buildEvent instanceof BuildIssueEvent buildIssueEvent) {
                addBuildIssue(buildIssueEvent.getIssue(), buildIssueEvent.getKind());
            }
            else {
                mySyncView.onEvent(mySyncId, buildEvent);
            }
        });
    }

    public synchronized void addWarning(@Nonnull LocalizeValue text, @Nonnull LocalizeValue description) {
        addWarning(text, description, null);
    }

    @Override
    public void addBuildIssue(@Nonnull BuildIssue issue, @Nonnull MessageEvent.Kind kind) {
        doIfImportInProcessOrPostpone(() -> {
            LocalizeValue issueId = LocalizeValue.join(issue.getTitle(), issue.getDescription());
            if (!newIssue(issueId)) {
                return;
            }
            mySyncView.onEvent(mySyncId, myFactory.createBuildIssueEvent(mySyncId, issue, kind));
            hasErrors = hasErrors || kind == MessageEvent.Kind.ERROR;
        });
    }

    public synchronized void addWarning(
        @Nonnull LocalizeValue text,
        @Nonnull LocalizeValue description,
        @Nullable FilePosition filePosition
    ) {
        doIfImportInProcess(() -> {
            LocalizeValue issueId = LocalizeValue.join(text, description, LocalizeValue.of(String.valueOf(filePosition)));
            if (!newIssue(issueId)) {
                return;
            }
            if (filePosition == null) {
                mySyncView.onEvent(mySyncId, myFactory.createMessageEvent(
                    mySyncId,
                    MessageEvent.Kind.WARNING,
                    MavenBuildNotification.COMPILER,
                    text.get(),
                    description.get()
                ));
            }
            else {
                mySyncView.onEvent(mySyncId, myFactory.createFileMessageEvent(
                    mySyncId,
                    MessageEvent.Kind.WARNING,
                    MavenBuildNotification.COMPILER,
                    text.get(),
                    description.get(),
                    filePosition
                ));
            }
        });
    }

    private boolean newIssue(@Nonnull LocalizeValue issueId) {
        return shownIssues.add(issueId);
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
        List<Pair<Object, LocalizeValue>> tasks = new ArrayList<>(myStartedSet);
        Collections.reverse(tasks);
        debugLog("Tasks " + tasks + " are not completed! Force complete");
        for (Pair<Object, LocalizeValue> task : tasks) {
            completeTask(
                task.getFirst(),
                task.getSecond(),
                myFactory.newFailure().message(MavenSyncLocalize.mavenSyncFailureTerminated(exitCode)).createResult()
            );
        }

        mySyncView.onEvent(mySyncId, myFactory.createFinishBuildEvent(
            mySyncId,
            null,
            System.currentTimeMillis(),
            "",
            myFactory.newFailure().message(MavenSyncLocalize.mavenSyncFailureTerminated(exitCode)).createResult()
        ));
        finished = true;
        started = false;
    }

    public synchronized void startWrapperResolving() {
        if (!started || finished) {
            startImport(true);
        }
        startTask(mySyncId, MavenSyncLocalize.mavenSyncWrapper());
    }

    public synchronized void finishWrapperResolving() {
        finishWrapperResolving(null);
    }

    public synchronized void finishWrapperResolving(@Nullable Throwable e) {
        if (e != null) {
            // TODO !
//            addBuildIssue(new BuildIssue() {
//                @Override
//                @Nonnull
//                public String getTitle() {
//                    return MavenSyncLocalize.mavenSyncWrapperFailure().get();
//                }
//
//                @Override
//                @Nonnull
//                public String getDescription() {
//                    return MavenSyncLocalize.mavenSyncWrapperFailureDescription(e.getLocalizedMessage(), OpenMavenSettingsQuickFix.ID).get();
//                }
//
//                @Override
//                @Nonnull
//                public List<BuildIssueQuickFix> getQuickFixes() {
//                    return Collections.singletonList(new OpenMavenSettingsQuickFix());
//                }
//
//                @Override
//                @Nullable
//                public Navigatable getNavigatable(@Nonnull Project project) {
//                    return null;
//                }
//            }, MessageEvent.Kind.WARNING);
        }
        completeTask(mySyncId, MavenSyncLocalize.mavenSyncWrapper(), myFactory.createSuccessResult());
    }

    public synchronized void notifyReadingProblems(@Nonnull VirtualFile file) {
        doIfImportInProcess(() -> {
            debugLog("reading problems in " + file);
            hasErrors = true;
            LocalizeValue desc = MavenSyncLocalize.mavenSyncFailureErrorReadingFile(file.getPath());
            mySyncView.onEvent(mySyncId, myFactory.createFileMessageEvent(
                mySyncId,
                MessageEvent.Kind.ERROR,
                MavenBuildNotification.BUILD_ERROR,
                desc.get(),
                desc.get(),
                new FilePosition(new File(file.getPath()), -1, -1)
            ));
        });
    }

    public synchronized void notifyDownloadSourcesProblem(@Nonnull Exception e) {
        MessageEvent messageEvent;
        if (e instanceof RescheduledMavenDownloadJobException) {
            // a new job was submitted so no need to show anything to the user
            messageEvent = null;
        }
        else if (e instanceof CancellationException) {
            // a normal cancellation happened
            LocalizeValue message = MavenProjectLocalize.mavenDownloadingCancelled();
            messageEvent = myFactory.createMessageEvent(
                mySyncId,
                MessageEvent.Kind.INFO,
                MavenBuildNotification.BUILD_ERROR,
                message.get(),
                message.get()
            );
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
            NotificationGroup group = problem.isError()
                ? MavenBuildNotification.BUILD_ERROR
                : MavenBuildNotification.BUILD_WARN;
            MessageEvent.Kind kind = problem.isError() ? MessageEvent.Kind.ERROR : MessageEvent.Kind.WARNING;
            FilePosition position = getFilePosition(problem);
            String message = problem.getDescription() != null
                ? problem.getDescription()
                : MavenSyncLocalize.mavenSyncFailureErrorUndefinedMessage().get();
            String detailedMessage = problem.getDescription() != null
                ? problem.getDescription()
                : MavenSyncLocalize.mavenSyncFailureErrorUndefinedDetailedMessage(problem.getPath()).get();
            FileMessageEvent eventImpl = myFactory.createFileMessageEvent(mySyncId, kind, group, message, detailedMessage, position);
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

    private synchronized void doFinish(boolean showFullSyncQuickFix) {
        if (!started || finished) {
            return;
        }
        if (syncTransactionStarted) {
            debugLog("Maven sync: sync transaction is still not finished, postpone build finish event");
            return;
        }
        List<Pair<Object, LocalizeValue>> tasks = new ArrayList<>(myStartedSet);
        Collections.reverse(tasks);
        debugLog("Tasks " + tasks + " are not completed! Force complete");
        for (Pair<Object, LocalizeValue> task : tasks) {
            completeTask(task.getFirst(), task.getSecond(), myFactory.createDerivedResult());
        }
        mySyncView.onEvent(mySyncId, myFactory.createFinishBuildEvent(
            mySyncId,
            null,
            System.currentTimeMillis(),
            "",
            hasErrors ? myFactory.newFailure().createResult() : myFactory.createDerivedResult()
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
            final String quickFixId = "maven.full.sync";
            mySyncView.onEvent(mySyncId, myFactory.createBuildIssueEvent(mySyncId, new BuildIssue() {
                @Override
                @Nonnull
                public LocalizeValue getTitle() {
                    return LocalizeValue.localizeTODO("Sync Finished");
                }

                @Override
                @Nonnull
                public LocalizeValue getDescription() {
                    return LocalizeValue.localizeTODO(
                        "Sync finished. If there is something wrong with the project model, <a href=\"" +
                            quickFixId + "\">reload all projects</a>\n"
                    );
                }

                @Override
                @Nonnull
                public List<BuildIssueQuickFix> getQuickFixes() {
                    return Collections.singletonList(new BuildIssueQuickFix() {
                        @Override
                        public String getId() {
                            return quickFixId;
                        }

                        @Override
                        public CompletableFuture<?> runQuickFix(Project project, DataProvider dataProvider) {
                            MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles();
                            return CompletableFuture.completedFuture(null);
                        }
                    });
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
            MavenGeneralSettings generalSettings = MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().generalSettings;
            if (hasUnresolved && generalSettings.isWorkOffline()) {
                final String quickFixId = "maven.offline.disable";
                mySyncView.onEvent(mySyncId, myFactory.createBuildIssueEvent(mySyncId, new BuildIssue() {
                    @Override
                    @Nonnull
                    public LocalizeValue getTitle() {
                        return LocalizeValue.localizeTODO("Dependency Resolution Failed");
                    }

                    @Override
                    @Nonnull
                    public LocalizeValue getDescription() {
                        return LocalizeValue.localizeTODO("<a href=\"" + quickFixId + "\">Switch Off Offline Mode</a>\n");
                    }

                    @Override
                    @Nonnull
                    public List<BuildIssueQuickFix> getQuickFixes() {
                        return Collections.singletonList(new BuildIssueQuickFix() {
                            @Override
                            public String getId() {
                                return quickFixId;
                            }

                            @Override
                            public CompletableFuture<?> runQuickFix(Project project, DataProvider dataProvider) {
                                MavenWorkspaceSettingsComponent.getInstance(project).getSettings().generalSettings.setWorkOffline(false);
                                return CompletableFuture.completedFuture(null);
                            }
                        });
                    }

                    @Override
                    @Nullable
                    public Navigatable getNavigatable(@Nonnull Project project) {
                        return null;
                    }
                }, MessageEvent.Kind.WARNING));
            }
        }
        catch (Exception ignored) {
        }
    }

    private synchronized void showArtifactBuildIssue(
        @Nonnull ResolveDescriptor resolveDescriptor,
        @Nonnull String dependency,
        @Nonnull LocalizeValue errorMessage
    ) {
        doIfImportInProcess(() -> {
            hasErrors = true;
            hasUnresolved = true;
            LocalizeValue actionText = resolveDescriptor.getTaskName();
            LocalizeValue error = resolveDescriptor.getErrorMessage(dependency);
            startTask(mySyncId, actionText);
            LocalizeValue details = errorMessage.orIfEmpty(error);
            BuildIssue buildIssue = new BuildIssue() {
                @Override
                @Nonnull
                public LocalizeValue getTitle() {
                    return error;
                }

                @Override
                @Nonnull
                public LocalizeValue getDescription() {
                    return details;
                }

                @Override
                @Nonnull
                public List<BuildIssueQuickFix> getQuickFixes() {
                    return Collections.emptyList();
                }

                @Override
                @Nullable
                public Navigatable getNavigatable(@Nonnull Project project) {
                    return null;
                }
            };
            mySyncView.onEvent(mySyncId, myFactory.createBuildIssueEvent(actionText, buildIssue, MessageEvent.Kind.ERROR));
            addText(mySyncId, error, false);
        });
    }

    public void showArtifactBuildIssue(
        @Nonnull MavenServerConsoleIndicator.ResolveType type,
        @Nonnull String dependency,
        @Nonnull LocalizeValue errorMessage
    ) {
        showArtifactBuildIssue(ResolveDescriptor.of(type), dependency, errorMessage);
    }

    public synchronized void showBuildIssue(@Nonnull BuildIssue buildIssue) {
        doIfImportInProcess(() -> {
            hasErrors = true;
            hasUnresolved = true;
            ResolveDescriptor resolveDescriptor = ResolveDescriptor.of(MavenServerConsoleIndicator.ResolveType.DEPENDENCY);
            startTask(mySyncId, resolveDescriptor.getTaskName());
            mySyncView.onEvent(mySyncId, myFactory.createBuildIssueEvent(resolveDescriptor, buildIssue, MessageEvent.Kind.ERROR));
        });
    }

    public synchronized void showBuildIssue(@Nonnull BuildIssue buildIssue, @Nonnull MessageEvent.Kind kind) {
        doIfImportInProcess(() -> {
            hasErrors = hasErrors || kind == MessageEvent.Kind.ERROR;
            ResolveDescriptor resolveDescriptor = ResolveDescriptor.of(MavenServerConsoleIndicator.ResolveType.DEPENDENCY);
            startTask(mySyncId, resolveDescriptor.getTaskName());
            mySyncView.onEvent(mySyncId, myFactory.createBuildIssueEvent(resolveDescriptor, buildIssue, kind));
        });
    }

    private synchronized void startTask(@Nonnull Object parentId, @Nonnull LocalizeValue taskName) {
        doIfImportInProcess(() -> {
            debugLog("Maven sync: start " + taskName);
            if (myStartedSet.add(Pair.create(parentId, taskName))) {
                mySyncView.onEvent(mySyncId, myFactory.createStartEvent(taskName, parentId, System.currentTimeMillis(), taskName.get()));
            }
        });
    }

    private synchronized void completeTask(@Nonnull Object parentId, @Nonnull LocalizeValue taskName, @Nonnull EventResult result) {
        doIfImportInProcess(() -> {
            hasErrors = hasErrors || result instanceof FailureResult;

            debugLog("Maven sync: complete " + taskName + " with " + result);
            if (myStartedSet.remove(Pair.create(parentId, taskName))) {
                mySyncView.onEvent(
                    mySyncId,
                    myFactory.createFinishEvent(taskName, parentId, System.currentTimeMillis(), taskName.get(), result)
                );
            }
        });
    }

    public void finishPluginResolution() {
        completeUmbrellaEvents(ResolveDescriptor.of(MavenServerConsoleIndicator.ResolveType.PLUGIN));
    }

    public void finishArtifactsDownload() {
        completeUmbrellaEvents(ResolveDescriptor.of(MavenServerConsoleIndicator.ResolveType.DEPENDENCY));
    }

    private synchronized void completeUmbrellaEvents(@Nonnull ResolveDescriptor resolveDescriptor) {
        doIfImportInProcess(() -> {
            LocalizeValue taskName = resolveDescriptor.getTaskName();
            completeTask(mySyncId, taskName, myFactory.createDerivedResult());
        });
    }

    private synchronized void downloadEventStarted(@Nonnull ResolveDescriptor resolveDescriptor, @Nonnull String dependency) {
        doIfImportInProcess(() -> {
            LocalizeValue downloadString = resolveDescriptor.getDownloadingMessage();
            LocalizeValue downloadArtifactString = resolveDescriptor.getDownloadingMessage(dependency);
            startTask(mySyncId, downloadString);
            startTask(downloadString, downloadArtifactString);
        });
    }

    private synchronized void downloadEventCompleted(@Nonnull ResolveDescriptor resolveDescriptor, @Nonnull String dependency) {
        doIfImportInProcess(() -> {
            LocalizeValue downloadingArtifactMessage = resolveDescriptor.getDownloadingMessage(dependency);
            addText(downloadingArtifactMessage, downloadingArtifactMessage, true);
            completeTask(resolveDescriptor.getDownloadingMessage(), downloadingArtifactMessage, myFactory.createSuccessResult(false));
        });
    }

    private synchronized void downloadEventFailed(
        @Nonnull ResolveDescriptor resolveDescriptor,
        @Nonnull String dependency,
        @Nonnull LocalizeValue error,
        @Nullable String stackTrace
    ) {
        doIfImportInProcess(() -> {
            LocalizeValue downloadingMessage = resolveDescriptor.getDownloadingMessage();
            LocalizeValue downloadingArtifactMessage = resolveDescriptor.getDownloadingMessage(dependency);

            if (isJavadocOrSource(dependency)) {
                addText(downloadingArtifactMessage, MavenSyncLocalize.mavenSyncFailureDependencyNotFound(dependency), true);
                completeTask(downloadingMessage, downloadingArtifactMessage, new MessageEventResult() {
                    @Override
                    public MessageEvent.Kind getKind() {
                        return MessageEvent.Kind.WARNING;
                    }

                    @Override
                    @Nonnull
                    public LocalizeValue getDetails() {
                        return MavenSyncLocalize.mavenSyncFailureDependencyNotFound(dependency);
                    }
                });
            }
            else {
                if (stackTrace != null && Registry.is("maven.spy.events.debug")) {
                    addText(downloadingArtifactMessage, LocalizeValue.of(stackTrace), false);
                }
                else {
                    addText(downloadingArtifactMessage, error, true);
                }
                completeTask(downloadingMessage, downloadingArtifactMessage, myFactory.newFailure().message(error).createResult());
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

    public synchronized void startTransaction() {
        syncTransactionStarted = true;
    }

    public synchronized void finishTransaction(boolean showFullSyncQuickFix) {
        syncTransactionStarted = false;
        finishImport(showFullSyncQuickFix);
    }

    @Override
    public synchronized void handleDownloadEvents(@Nonnull List<MavenArtifactEvent> downloadEvents) {
        for (MavenArtifactEvent e : downloadEvents) {
            ResolveDescriptor resolveDescriptor = ResolveDescriptor.of(e.getResolveType());
            String id = e.getDependencyId();
            switch (e.getArtifactEventType()) {
                case DOWNLOAD_STARTED -> downloadEventStarted(resolveDescriptor, id);
                case DOWNLOAD_COMPLETED -> downloadEventCompleted(resolveDescriptor, id);
                case DOWNLOAD_FAILED ->
                    downloadEventFailed(resolveDescriptor, id, LocalizeValue.ofNullable(e.getErrorMessage()), e.getStackTrace());
            }
        }
    }

    @Override
    public void handleConsoleEvents(@Nonnull List<MavenServerConsoleEvent> consoleEvents) {
        for (MavenServerConsoleEvent e : consoleEvents) {
            printMessage(e.getLevel(), LocalizeValue.ofNullable(e.getMessage()), e.getThrowable());
        }
    }

    public void printException(@Nonnull Throwable throwable) {
        printMessage(MavenServerConsoleIndicator.LEVEL_ERROR, LocalizeValue.localizeTODO("Embedded build failed"), throwable);
    }

    public void printMessage(int level, @Nonnull LocalizeValue message, @Nullable Throwable throwable) {
        if (isSuppressed(level)) {
            return;
        }

        boolean stdout = throwable == null
            && level != MavenServerConsoleIndicator.LEVEL_WARN
            && level != MavenServerConsoleIndicator.LEVEL_ERROR
            && level != MavenServerConsoleIndicator.LEVEL_FATAL;

        addText(composeLine(level, message), stdout);

        if (throwable != null) {
            String throwableText = ExceptionUtil.getThrowableText(throwable);
            if (Registry.is("maven.print.import.stacktraces") || Application.get().isUnitTestMode()) {
                addText(
                    LocalizeValue.join(
                        LINE_SEPARATOR,
                        composeLine(MavenServerConsoleIndicator.LEVEL_ERROR, LocalizeValue.of(throwableText))
                    ),
                    false
                );
            }
            else {
                addText(
                    LocalizeValue.join(
                        LINE_SEPARATOR,
                        composeLine(MavenServerConsoleIndicator.LEVEL_ERROR, LocalizeValue.ofNullable(throwable.getMessage()))
                    ),
                    false
                );
            }
        }
    }

    public boolean isSuppressed(int level) {
        return level < MavenProjectsManager.getInstance(myProject).getGeneralSettings().getOutputLevel().getLevel();
    }

    @Nonnull
    private LocalizeValue composeLine(int level, @Nonnull LocalizeValue message) {
        return MavenSyncLocalize.logLevelAndMessage(LEVEL_TO_PREFIX.get(level), message);
    }

    private static void debugLog(@Nonnull String s) {
        debugLog(s, null);
    }

    private static void debugLog(@Nonnull String s, @Nullable Throwable exception) {
        MavenLog.LOG.debug(s, exception);
    }
}
