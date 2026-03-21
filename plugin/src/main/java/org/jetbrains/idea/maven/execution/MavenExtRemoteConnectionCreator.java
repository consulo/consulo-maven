package org.jetbrains.idea.maven.execution;

import com.intellij.java.execution.configurations.RemoteConnection;
import com.intellij.java.execution.configurations.RemoteConnectionCreator;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.java.execution.configurations.OwnJavaParameters;
import jakarta.annotation.Nullable;

public class MavenExtRemoteConnectionCreator implements RemoteConnectionCreator {
    private final OwnJavaParameters myJavaParameters;
    private final MavenRunConfiguration myRunConfiguration;

    public MavenExtRemoteConnectionCreator(OwnJavaParameters javaParameters, MavenRunConfiguration runConfiguration) {
        myJavaParameters = javaParameters;
        myRunConfiguration = runConfiguration;
    }

    @Override
    @Nullable
    public RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
        for (MavenRemoteConnectionCreator creator : MavenRemoteConnectionCreator.EP_NAME.getExtensionList()) {
            RemoteConnection connection = creator.createRemoteConnection(myJavaParameters, myRunConfiguration);
            if (connection != null) {
                return connection;
            }
        }
        return null;
    }

    @Override
    public boolean isPollConnection() {
        return true;
    }
}
