package consulo.maven.project;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.maven.module.extension.MavenModuleExtension;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.project.Project;
import consulo.project.startup.PostStartupActivity;
import consulo.ui.UIAccess;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 06/05/2023
 */
@ExtensionImpl
public class MavenProjectStartActivity implements PostStartupActivity, DumbAware
{
	@Override
	public void runActivity(@Nonnull Project project, @Nonnull UIAccess uiAccess)
	{
		if(!ModuleExtensionHelper.getInstance(project).hasModuleExtension(MavenModuleExtension.class))
		{
			return;
		}

		MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);

		projectsManager.doInit();
	}
}
