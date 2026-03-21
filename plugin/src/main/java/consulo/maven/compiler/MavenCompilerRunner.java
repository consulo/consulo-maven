package consulo.maven.compiler;

import com.intellij.java.execution.impl.DefaultJavaProgramRunner;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.build.ui.progress.BuildProgress;
import consulo.build.ui.progress.BuildProgressDescriptor;
import consulo.compiler.*;
import consulo.compiler.scope.CompileScope;
import consulo.compiler.util.ModuleCompilerUtil;
import consulo.dataContext.DataContext;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.localize.LocalizeValue;
import consulo.maven.icon.MavenIconGroup;
import consulo.maven.module.extension.MavenModuleExtension;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.module.Module;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputType;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import org.jetbrains.idea.maven.execution.MavenCommandLineState;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.localize.MavenLocalize;
import org.jetbrains.idea.maven.project.MaveOverrideCompilerPolicy;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author VISTALL
 * @since 18/04/2023
 */
@ExtensionImpl
public class MavenCompilerRunner implements CompilerRunner {
    private static final YesResult YES = new YesResult(MavenIconGroup.mavenbuild());

    private final Project myProject;
    private final ModuleExtensionHelper myModuleExtensionHelper;

    @Inject
    public MavenCompilerRunner(Project project, ModuleExtensionHelper moduleExtensionHelper) {
        myProject = project;
        myModuleExtensionHelper = moduleExtensionHelper;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return MavenLocalize.mavenName();
    }

    @Nonnull
    @Override
    public Result checkAvailable(@Nonnull DataContext dataContext) {
        if (!myModuleExtensionHelper.hasModuleExtension(MavenModuleExtension.class)) {
            return NO;
        }

        MavenGeneralSettings generalSettings = MavenProjectsManager.getInstance(myProject).getGeneralSettings();
        if (generalSettings.getOverrideCompilePolicy() == MaveOverrideCompilerPolicy.DISABLED) {
            return NO;
        }

        return YES;
    }

    @Override
    public boolean build(CompileDriver compileDriver, CompileContextEx context, BuildProgress<BuildProgressDescriptor> buildProgress, boolean isRebuild, boolean forceCompile, boolean onlyCheckStatus) throws ExitException {
        MavenGeneralSettings generalSettings = MavenProjectsManager.getInstance(myProject).getGeneralSettings();
        if (generalSettings.getOverrideCompilePolicy() == MaveOverrideCompilerPolicy.DISABLED) {
            // must be never entered due #isAvailable() check
            return false;
        }

        MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(myProject);
        MavenExplicitProfiles explicitProfiles = mavenProjectsManager.getExplicitProfiles();

        CompileScope compileScope = context.getCompileScope();
        List<String> goals = new ArrayList<>();
        switch (generalSettings.getOverrideCompilePolicy()) {
            case BY_COMPILE:
                goals.add("compile");
                if (compileScope.includeTestScope()) {
                    goals.add("test-compile");
                }
                break;
            case BY_PACKAGE:
                goals.add("package");
                break;
        }

        List<Module> modules = new ArrayList<>(List.of(compileScope.getAffectedModules()));
        ModuleCompilerUtil.sortModules(context.getProject(), modules);

        goals.add("-pl");
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (Module module : modules) {
            MavenProject mavenProject = mavenProjectsManager.findProject(module);
            if (mavenProject == null) {
                continue;
            }

            MavenId mavenId = mavenProject.getMavenId();

            if (i != 0) {
                builder.append(",");
            }

            builder.append(mavenId.getGroupId()).append(":").append(mavenId.getArtifactId());

            i++;
        }

        goals.add(builder.toString());

        // -am -amd
        goals.add("-am");

        List<MavenProject> rootProjects = mavenProjectsManager.getRootProjects();
        if (rootProjects.isEmpty()) {
            // sometimes when project was failed to import
            return false;
        }

        MavenProject mavenProject = rootProjects.get(0);

        MavenRunnerParameters params = new MavenRunnerParameters(true, mavenProject.getDirectory(), goals, explicitProfiles);
        params.setResolveToWorkspace(true);

        MavenRunnerSettings settings = MavenRunner.getInstance(myProject).getSettings().clone();
        // do not allow run tests while compilation
        settings.setSkipTests(true);

        // Create run configuration to get a MavenCommandLineState for process creation.
        // We start the process directly and pipe its output to the compiler's buildProgress
        // to avoid creating a duplicate Build ToolWindow session.
        RunnerAndConfigurationSettings configSettings =
            MavenRunConfigurationType.createRunnerAndConfigurationSettings(null, settings, params, myProject);
        MavenRunConfiguration runConfig = (MavenRunConfiguration) configSettings.getConfiguration();
        ExecutionEnvironment env = new ExecutionEnvironment(
            DefaultRunExecutor.getRunExecutorInstance(),
            DefaultJavaProgramRunner.getInstance(),
            configSettings,
            myProject
        );
        MavenCommandLineState commandState = new MavenCommandLineState(env, runConfig);

        CountDownLatch latch = new CountDownLatch(1);

        ProcessHandler processHandler = null;

        try {
            processHandler = ReadAction.compute(() -> commandState.startMavenProcess());
        }
        catch (ExecutionException e) {
            context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
            return false;
        }

        if (processHandler == null) {
            context.addMessage(CompilerMessageCategory.ERROR, "Failed to start Maven process", null, -1, -1);
            return true;
        }

        processHandler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@Nonnull ProcessEvent event, @Nonnull Key outputType) {
                buildProgress.output(event.getText(), !ProcessOutputType.isStderr(outputType));
            }

            @Override
            public void processTerminated(@Nonnull ProcessEvent event) {
                latch.countDown();

                if (event.getExitCode() != 0) {
                    buildProgress.fail();
                }
            }
        });

        processHandler.startNotify();

        try {
            latch.await();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return true;
    }
}
