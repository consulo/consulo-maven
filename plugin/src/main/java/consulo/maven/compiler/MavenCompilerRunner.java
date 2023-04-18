package consulo.maven.compiler;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.compiler.CompileContextEx;
import consulo.compiler.CompileDriver;
import consulo.compiler.CompilerRunner;
import consulo.compiler.ExitException;
import consulo.localize.LocalizeValue;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.process.cmd.ParametersListUtil;
import consulo.project.Project;
import jakarta.inject.Inject;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.tasks.TasksBundle;

import javax.annotation.Nonnull;
import java.util.Collections;

/**
 * @author VISTALL
 * @since 18/04/2023
 */
@ExtensionImpl
public class MavenCompilerRunner implements CompilerRunner
{
	private final Project myProject;

	@Inject
	public MavenCompilerRunner(Project project)
	{
		myProject = project;
	}

	@Nonnull
	@Override
	public LocalizeValue getName()
	{
		return LocalizeValue.localizeTODO("Maven");
	}

	@Override
	public boolean isAvailable(CompileContextEx compileContextEx)
	{
		return MavenProjectsManager.getInstance(myProject).getGeneralSettings().isOverrideBuiltinCompiler();
	}

	@Override
	public boolean build(CompileDriver compileDriver, CompileContextEx context, boolean isRebuild, boolean forceCompile, boolean onlyCheckStatus) throws ExitException
	{
		MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(myProject);
		final MavenExplicitProfiles explicitProfiles = mavenProjectsManager.getExplicitProfiles();
		final MavenRunner mavenRunner = MavenRunner.getInstance(myProject);

		boolean changed = false;
		for(MavenProject mavenProject : mavenProjectsManager.getRootProjects())
		{
			MavenRunnerParameters params = new MavenRunnerParameters(true, mavenProject.getDirectory(), ParametersListUtil.parse("package"), explicitProfiles);

			ProgressIndicator indicator = ProgressManager.getGlobalProgressIndicator();

			changed |= mavenRunner.runBatch(Collections.singletonList(params), null, null, TasksBundle.message("maven.tasks.executing"), indicator);
		}

		return changed;
	}
}
