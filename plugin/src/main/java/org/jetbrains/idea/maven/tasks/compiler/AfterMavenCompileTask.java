package org.jetbrains.idea.maven.tasks.compiler;

import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.AfterCompilerTask;

/**
 * @author VISTALL
 * @since 2023-01-21
 */
@ExtensionImpl
public class AfterMavenCompileTask extends MavenCompileTask implements AfterCompilerTask {
    public AfterMavenCompileTask() {
        super(false);
    }
}
