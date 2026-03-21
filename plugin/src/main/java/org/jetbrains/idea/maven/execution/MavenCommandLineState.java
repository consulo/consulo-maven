package org.jetbrains.idea.maven.execution;

import com.intellij.java.execution.configurations.JavaCommandLineState;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.SystemInfo;
import consulo.build.ui.BuildDescriptor;
import consulo.build.ui.BuildViewManager;
import consulo.build.ui.DefaultBuildDescriptor;
import consulo.build.ui.event.BuildEvent;
import consulo.build.ui.event.BuildEventFactory;
import consulo.build.ui.event.StartBuildEvent;
import consulo.execution.DefaultExecutionResult;
import consulo.execution.ExecutionResult;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.execution.ui.console.ConsoleView;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.externalSystem.model.task.ExternalSystemTaskType;
import consulo.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager;
import consulo.ide.impl.idea.build.BuildTreeFilters;
import consulo.ide.impl.idea.build.BuildView;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DefaultActionGroup;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.buildtool.BuildToolConsoleProcessAdapter;
import org.jetbrains.idea.maven.buildtool.MavenBuildEventProcessor;
import org.jetbrains.idea.maven.execution.run.MavenBuildHandlerFilterSpyWrapper;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * @author VISTALL
 * @since 2026-02-23
 */
public class MavenCommandLineState extends JavaCommandLineState {
    private final MavenRunConfiguration myConfiguration;

    public MavenCommandLineState(@Nonnull ExecutionEnvironment environment, MavenRunConfiguration configuration) {
        super(environment);
        myConfiguration = configuration;
    }

    @Override
    @RequiredReadAction
    protected OwnJavaParameters createJavaParameters() throws ExecutionException {
        return myConfiguration.createJavaParameters(getEnvironment().getProject());
    }

    @Nonnull
    @Override
    public ExecutionResult execute(@Nonnull Executor executor, @Nonnull ProgramRunner runner) throws ExecutionException {
        ExternalSystemTaskId taskId =
            ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myConfiguration.getProject());

        String workingDir = getEnvironment().getProject().getBasePath();

        Function<String, String> targetFileMapper = path -> {
            return path != null && SystemInfo.isWindows && path.charAt(0) == '/' ? path.substring(1) : path;
        };

        DefaultBuildDescriptor descriptor =
            new DefaultBuildDescriptor(taskId, myConfiguration.getName(), workingDir, System.currentTimeMillis());

        final ProcessHandler processHandler = startProcess();

        if (MavenRunConfigurationType.isDelegate(getEnvironment())) {
            return doDelegateBuildExecute(executor, runner, taskId, descriptor, processHandler, targetFileMapper);
        }
        else {
            return doRunExecute(executor, runner, taskId, descriptor, processHandler, targetFileMapper);
        }
    }

    private @Nullable BuildView createBuildView(@NotNull Executor executor,
                                                @NotNull BuildDescriptor descriptor,
                                                @NotNull ProcessHandler processHandler) throws ExecutionException {
        ConsoleView console = createConsole(executor, processHandler, myConfiguration.getProject());
        if (console == null) {
            return null;
        }
        Project project = myConfiguration.getProject();
        ExternalSystemRunConfigurationViewManager viewManager = project.getInstance(ExternalSystemRunConfigurationViewManager.class);
        return new BuildView(project, console, descriptor, "build.toolwindow.run.selection.state", viewManager) {
            @Override
            public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
                super.onEvent(buildId, event);
                viewManager.onEvent(buildId, event);
            }
        };
    }

    private ConsoleView createConsole(Executor executor, ProcessHandler processHandler, Project project) throws ExecutionException {
        return createConsole(executor);
    }

    private boolean emulateTerminal() {
        return false; // TODO !
    }

    public ExecutionResult doRunExecute(@NotNull Executor executor,
                                        @NotNull ProgramRunner runner,
                                        ExternalSystemTaskId taskId,
                                        DefaultBuildDescriptor descriptor,
                                        ProcessHandler processHandler,
                                        @NotNull Function<String, String> targetFileMapper) throws ExecutionException {
        final BuildView buildView = createBuildView(executor, descriptor, processHandler);

        if (buildView == null) {
            MavenLog.LOG.warn("buildView is null for " + myConfiguration.getName());
        }

        BuildEventFactory eventFactory = getEnvironment().getProject().getApplication().getInstance(BuildEventFactory.class);

        MavenBuildEventProcessor eventProcessor =
            new MavenBuildEventProcessor(myConfiguration, buildView, descriptor, taskId, targetFileMapper, ctx ->
                eventFactory.createStartBuildEvent(descriptor, ""), useMaven4());

        processHandler.addProcessListener(new BuildToolConsoleProcessAdapter(eventProcessor));
        if (emulateTerminal()) {
            buildView.attachToProcess(processHandler);
        }
        else {
            buildView.attachToProcess(new MavenBuildHandlerFilterSpyWrapper(processHandler, useMaven4(), false));
        }

        AnAction[] actions = new AnAction[]{BuildTreeFilters.createFilteringActionsGroup(buildView)};
        DefaultExecutionResult res = new DefaultExecutionResult(buildView, processHandler, actions);
        List<AnAction> restartActions = new ArrayList<>();
        restartActions.add(new MavenRebuildAction(getEnvironment()));

        if (MavenResumeAction.isApplicable(getEnvironment().getProject(), getJavaParameters(), myConfiguration)) {
            MavenResumeAction resumeAction =
                new MavenResumeAction(res.getProcessHandler(), runner, getEnvironment(), eventProcessor.getParsingContext());
            restartActions.add(resumeAction);
        }
        res.setRestartActions(restartActions.toArray(AnAction.EMPTY_ARRAY));
        return res;
    }

    public ExecutionResult doDelegateBuildExecute(@NotNull Executor executor,
                                                  @NotNull ProgramRunner runner,
                                                  ExternalSystemTaskId taskId,
                                                  DefaultBuildDescriptor descriptor,
                                                  ProcessHandler processHandler,
                                                  Function<String, String> targetFileMapper) throws ExecutionException {
        BuildEventFactory eventFactory = getEnvironment().getProject().getApplication().getInstance(BuildEventFactory.class);

        ConsoleView consoleView = createConsole(executor, processHandler, myConfiguration.getProject());
        BuildViewManager viewManager = getEnvironment().getProject().getService(BuildViewManager.class);
        descriptor.withProcessHandler(new MavenBuildHandlerFilterSpyWrapper(processHandler, useMaven4(), false), null);
        descriptor.withExecutionEnvironment(getEnvironment());
        StartBuildEvent startBuildEvent = eventFactory.createStartBuildEvent(descriptor, "");
        boolean withResumeAction = MavenResumeAction.isApplicable(getEnvironment().getProject(), getJavaParameters(), myConfiguration);
        MavenBuildEventProcessor eventProcessor =
            new MavenBuildEventProcessor(myConfiguration, viewManager, descriptor, taskId,
                targetFileMapper, getStartBuildEventSupplier(runner, processHandler, descriptor, startBuildEvent, withResumeAction),
                useMaven4()
            );

        processHandler.addProcessListener(new BuildToolConsoleProcessAdapter(eventProcessor));
        DefaultExecutionResult res = new DefaultExecutionResult(consoleView, processHandler, new DefaultActionGroup());
        //res.setRestartActions(new JvmToggleAutoTestAction());
        return res;
    }

    private @NotNull Function<MavenParsingContext, StartBuildEvent> getStartBuildEventSupplier(@NotNull ProgramRunner runner,
                                                                                               ProcessHandler processHandler,
                                                                                               DefaultBuildDescriptor descriptor,
                                                                                               StartBuildEvent startBuildEvent,
                                                                                               boolean withResumeAction) {
        return ctx -> {
            descriptor.withRestartAction(new MavenRebuildAction(getEnvironment()));
            if (withResumeAction) {
                descriptor.withRestartAction(new MavenResumeAction(processHandler, runner, getEnvironment(), ctx));
            }
            return startBuildEvent;
        };
    }

    public boolean useMaven4() {
        return false;
    }

    /**
     * Starts the Maven process and returns the ProcessHandler.
     * Called by MavenCompilerRunner to pipe output directly to the compiler's BuildProgress.
     */
    public ProcessHandler startMavenProcess() throws ExecutionException {
        return startProcess();
    }

    @Override
    protected void buildProcessHandler(@Nonnull ProcessHandlerBuilder builder) throws ExecutionException {
        super.buildProcessHandler(builder);

        builder.shouldDestroyProcessRecursively(true);
    }

    @Override
    protected void setupProcessHandler(@Nonnull ProcessHandler handler) {
        super.setupProcessHandler(handler);
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void processTerminated(ProcessEvent event) {
                MavenProjectsManager.getInstance(getEnvironment().getProject()).updateProjectTargetFolders();
            }
        });
    }
}
