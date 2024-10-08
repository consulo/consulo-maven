package org.jetbrains.idea.maven.tasks.compiler;

import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.BeforeCompileTask;

/**
 * @author VISTALL
 * @since 2023-01-21
 */
@ExtensionImpl
public class BeforeMavenCompileTask extends MavenCompileTask implements BeforeCompileTask {
    public BeforeMavenCompileTask() {
        super(true);
    }
}
