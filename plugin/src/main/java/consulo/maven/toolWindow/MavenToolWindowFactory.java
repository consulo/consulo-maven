package consulo.maven.toolWindow;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.maven.icon.MavenIconGroup;
import consulo.maven.module.extension.MavenModuleExtension;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowFactory;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.ui.image.Image;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2019-11-09
 */
@ExtensionImpl
public class MavenToolWindowFactory implements ToolWindowFactory, DumbAware
{
	@Nonnull
	@Override
	public String getId()
	{
		return "Maven";
	}

	@RequiredUIAccess
	@Override
	public void createToolWindowContent(@Nonnull Project project, @Nonnull ToolWindow toolWindow)
	{
		MavenProjectsNavigator navigator = MavenProjectsNavigator.getInstance(project);

		navigator.initToolWindow(toolWindow);
	}

	@Nonnull
	@Override
	public ToolWindowAnchor getAnchor()
	{
		return ToolWindowAnchor.RIGHT;
	}

	@Nonnull
	@Override
	public Image getIcon()
	{
		return MavenIconGroup.toolwindowmaven();
	}

	@Nonnull
	@Override
	public LocalizeValue getDisplayName()
	{
		return LocalizeValue.of("Maven");
	}

	@Override
	public boolean validate(@Nonnull Project project)
	{
		return ModuleExtensionHelper.getInstance(project).hasModuleExtension(MavenModuleExtension.class);
	}
}
