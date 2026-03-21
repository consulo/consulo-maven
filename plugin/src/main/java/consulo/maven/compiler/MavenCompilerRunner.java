package consulo.maven.compiler;

import consulo.annotation.component.ExtensionImpl;
import consulo.build.ui.progress.BuildProgress;
import consulo.build.ui.progress.BuildProgressDescriptor;
import consulo.compiler.*;
import consulo.compiler.scope.CompileScope;
import consulo.compiler.util.ModuleCompilerUtil;
import consulo.dataContext.DataContext;
import consulo.localize.LocalizeValue;
import consulo.maven.icon.MavenIconGroup;
import consulo.maven.module.extension.MavenModuleExtension;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.module.Module;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.localize.MavenLocalize;
import org.jetbrains.idea.maven.project.MaveOverrideCompilerPolicy;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

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

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(true);

        // Use the delegate-build path: MavenCommandLineState.doDelegateBuildExecute() routes output
        // through BuildViewManager with full spy-event parsing (structured tree in Build ToolWindow).
        MavenRunConfigurationType.runConfiguration(
            myProject, params, null, settings,
            descriptor -> {
                if (descriptor == null) {
                    success.set(false);
                    latch.countDown();
                    return;
                }
                ProcessHandler handler = descriptor.getProcessHandler();
                if (handler == null) {
                    success.set(false);
                    latch.countDown();
                    return;
                }
                handler.addProcessListener(new ProcessAdapter() {
                    @Override
                    public void processTerminated(@Nonnull ProcessEvent event) {
                        success.set(event.getExitCode() == 0);
                        latch.countDown();
                    }
                });
                // Safety: process may already be terminated before listener was added
                if (handler.isProcessTerminated()) {
                    latch.countDown();
                }
            },
            true  // isDelegateBuild → doDelegateBuildExecute() → BuildViewManager
        );

        try {
            latch.await();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!success.get()) {
            context.addMessage(CompilerMessageCategory.ERROR, "Maven compilation failed", null, -1, -1);
        }

        return true;
    }
}
