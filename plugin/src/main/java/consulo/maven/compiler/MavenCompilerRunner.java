package consulo.maven.compiler;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.compiler.*;
import consulo.compiler.scope.CompileScope;
import consulo.compiler.util.ModuleCompilerUtil;
import consulo.localize.LocalizeValue;
import consulo.maven.icon.MavenIconGroup;
import consulo.maven.module.extension.MavenModuleExtension;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.module.Module;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.project.Project;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.localize.MavenLocalize;
import org.jetbrains.idea.maven.localize.MavenTasksLocalize;
import org.jetbrains.idea.maven.project.MaveOverrideCompilerPolicy;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.tasks.TasksBundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author VISTALL
 * @since 18/04/2023
 */
@ExtensionImpl
public class MavenCompilerRunner implements CompilerRunner {
    private final Project myProject;
    private final ModuleExtensionHelper myModuleExtensionHelper;

    @Inject
    public MavenCompilerRunner(Project project, ModuleExtensionHelper moduleExtensionHelper) {
        myProject = project;
        myModuleExtensionHelper = moduleExtensionHelper;
    }

    @Nonnull
    @Override
    public Image getBuildIcon() {
        return MavenIconGroup.mavenbuild();
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return MavenLocalize.mavenName();
    }

    @Override
    public boolean isAvailable() {
        if (!myModuleExtensionHelper.hasModuleExtension(MavenModuleExtension.class)) {
            return false;
        }

        MavenGeneralSettings generalSettings = MavenProjectsManager.getInstance(myProject).getGeneralSettings();
        return generalSettings.getOverrideCompilePolicy() != MaveOverrideCompilerPolicy.DISABLED;
    }

    @Override
    public boolean build(CompileDriver compileDriver, CompileContextEx context, boolean isRebuild, boolean forceCompile, boolean onlyCheckStatus) throws ExitException {
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

        ProgressIndicator indicator = ProgressManager.getGlobalProgressIndicator();

        MavenRunner mavenRunner = MavenRunner.getInstance(context.getProject());

        MavenRunnerSettings settings = MavenRunner.getInstance(myProject).getSettings().clone();
        // do not allow run tests while compilation
        settings.setSkipTests(true);

        if (!mavenRunner.runBatch(Collections.singletonList(params), null, settings, MavenTasksLocalize.mavenTasksExecuting().get(), indicator)) {
            context.addMessage(CompilerMessageCategory.ERROR, "Compilation failed", null, -1, -1);
        }

        return true;
    }
}
