package org.jetbrains.idea.maven.execution;

import com.intellij.java.execution.configurations.RemoteConnection;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.java.execution.configurations.OwnJavaParameters;
import jakarta.annotation.Nullable;

/**
 * Extension point for creating remote debug connections for Maven run configurations.
 * Implementations detect specific Maven goals that fork JVM processes and inject
 * JDWP debug parameters so the IDE debugger can attach to the forked process.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MavenRemoteConnectionCreator {
    ExtensionPointName<MavenRemoteConnectionCreator> EP_NAME =
        ExtensionPointName.create(MavenRemoteConnectionCreator.class);

    @Nullable
    RemoteConnection createRemoteConnection(OwnJavaParameters javaParameters, MavenRunConfiguration runConfiguration);
}
