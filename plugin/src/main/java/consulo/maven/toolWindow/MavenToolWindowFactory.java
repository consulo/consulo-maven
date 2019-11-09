package consulo.maven.toolWindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2019-11-09
 */
public class MavenToolWindowFactory implements ToolWindowFactory, DumbAware
{
	@Override
	public void createToolWindowContent(@Nonnull Project project, @Nonnull ToolWindow toolWindow)
	{
		MavenProjectsNavigator navigator = MavenProjectsNavigator.getInstance(project);

		navigator.initToolWindow((ToolWindowEx) toolWindow);
	}
}
