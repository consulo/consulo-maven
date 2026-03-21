package org.jetbrains.idea.maven.execution;

import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.execution.configurations.RemoteConnection;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.process.ExecutionException;
import consulo.process.cmd.ParametersList;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.regex.Pattern;

@ExtensionImpl
public class ExecRemoteConnectionCreator implements MavenRemoteConnectionCreator {
    private static final Pattern EXEC_MAVEN_PLUGIN_PATTERN =
        Pattern.compile("org[.]codehaus[.]mojo:exec-maven-plugin(:[\\d.]+)?:exec");

    @Override
    @Nullable
    public RemoteConnection createRemoteConnection(OwnJavaParameters javaParameters, MavenRunConfiguration runConfiguration) {
        List<String> programParams = javaParameters.getProgramParametersList().getList();
        boolean hasExecGoal = false;
        for (String param : programParams) {
            if ("exec:exec".equals(param) || EXEC_MAVEN_PLUGIN_PATTERN.matcher(param).matches()) {
                hasExecGoal = true;
                break;
            }
        }
        if (!hasExecGoal) return null;

        String port;
        try {
            port = DebuggerUtils.getInstance().findAvailableDebugAddress(DebuggerSettings.SOCKET_TRANSPORT).address();
        }
        catch (ExecutionException e) {
            return null;
        }

        String jdwpArg = "-agentlib:jdwp=transport=dt_socket,address=" + port + ",suspend=n,server=y";

        ParametersList programParametersList = javaParameters.getProgramParametersList();
        String execArgsPrefix = "-Dexec.args=";
        List<String> list = programParametersList.getList();
        int execArgsIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).startsWith(execArgsPrefix)) {
                execArgsIndex = i;
                break;
            }
        }

        if (execArgsIndex == -1) {
            programParametersList.add(execArgsPrefix + jdwpArg);
        }
        else {
            String existingArgs = programParametersList.get(execArgsIndex).substring(execArgsPrefix.length());
            programParametersList.set(execArgsIndex, execArgsPrefix + jdwpArg + " " + existingArgs);
        }

        return new RemoteConnection(true, "127.0.0.1", port, false);
    }
}
