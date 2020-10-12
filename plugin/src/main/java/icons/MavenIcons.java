package icons;

import com.intellij.icons.AllIcons;
import consulo.annotation.DeprecationInfo;
import consulo.maven.icon.MavenIconGroup;
import consulo.ui.image.Image;

@Deprecated
@DeprecationInfo("Use MavenIconGroup")
public class MavenIcons {
  public static final Image ChildrenProjects = MavenIconGroup.childrenProjects();
  public static final Image MavenLogo = MavenIconGroup.mavenLogo();
  public static final Image MavenPlugin = MavenIconGroup.mavenPlugin();
  // TODO [VISTALL] use file icon
  public static final Image MavenProject = MavenLogo; // 16x16
  public static final Image ModulesClosed = MavenIconGroup.modulesClosed();
  // TODO [VISTALL] use icon from platform new
  public static final Image OfflineMode = MavenIconGroup.offlineMode();
  public static final Image ParentProject = MavenIconGroup.parentProject();
  public static final Image PhasesClosed = AllIcons.Nodes.ConfigFolder; // 16x16
  public static final Image PluginGoal = MavenIconGroup.pluginGoal();
  public static final Image ProfilesClosed = MavenIconGroup.profilesClosed();
  public static final Image ToolWindowMaven = MavenIconGroup.toolWindowMaven();
  public static final Image UpdateFolders = MavenIconGroup.updateFolders();
  public static final Image Console = MavenIconGroup.ql_console();
}
