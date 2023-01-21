package org.jetbrains.idea.maven;

import consulo.application.AllIcons;
import consulo.annotation.DeprecationInfo;
import consulo.maven.icon.MavenIconGroup;
import consulo.ui.image.Image;

@Deprecated
@DeprecationInfo("Use MavenIconGroup")
public class MavenIcons
{
  public static final Image ChildrenProjects = MavenIconGroup.childrenprojects();
  public static final Image MavenLogo = MavenIconGroup.mavenlogo();
  public static final Image MavenPlugin = MavenIconGroup.mavenplugin();
  // TODO [VISTALL] use file icon
  public static final Image MavenProject = MavenLogo; // 16x16
  public static final Image ModulesClosed = MavenIconGroup.modulesclosed();
  public static final Image ParentProject = MavenIconGroup.parentproject();
  public static final Image PhasesClosed = AllIcons.Nodes.ConfigFolder; // 16x16
  public static final Image PluginGoal = MavenIconGroup.plugingoal();
  public static final Image ProfilesClosed = MavenIconGroup.profilesclosed();
  public static final Image ToolWindowMaven = MavenIconGroup.toolwindowmaven();
  public static final Image UpdateFolders = MavenIconGroup.updatefolders();
}
