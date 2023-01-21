package org.jetbrains.idea.maven.tasks.compiler;

import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.AfterCompilerTask;

/**
* @author VISTALL
* @since 21/01/2023
*/
@ExtensionImpl
public class AfterMavenCompileTask extends MavenCompileTask implements AfterCompilerTask
{
	public AfterMavenCompileTask()
	{
		super(false);
	}
}
