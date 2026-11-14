/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server;

import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.container.boot.ContainerPathManager;
import consulo.content.bundle.Sdk;
import consulo.execution.DefaultExecutionResult;
import consulo.execution.ExecutionResult;
import consulo.execution.configuration.CommandLineState;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ProgramRunner;
import consulo.java.execution.OwnSimpleJavaParameters;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.maven.rt.server.common.server.MavenServerEmbedder;
import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.ui.ex.action.AnAction;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.ClassPathUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.util.rmi.RemoteServer;
import org.apache.lucene.search.Query;
import org.jdom.Document;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The process of the maven server: {@code java} with the server classes and the libraries of the maven distribution.
 */
public class MavenServerCMDState extends CommandLineState {
    protected final Sdk myJdk;
    protected final @Nullable String myVmOptions;
    protected final @Nullable Integer myDebugPort;

    public MavenServerCMDState(Sdk jdk, @Nullable String vmOptions, @Nullable Integer debugPort) {
        super(null);
        myJdk = jdk;
        myVmOptions = vmOptions;
        myDebugPort = debugPort;
    }

    protected OwnSimpleJavaParameters createJavaParameters() throws ExecutionException {
        OwnSimpleJavaParameters params = new OwnSimpleJavaParameters();

        params.setWorkingDirectory(getWorkingDirectory());

        Map<String, String> defs = new HashMap<>(getMavenOpts());

        // pass ssl-related options
        for (Map.Entry<String, String> each : Platform.current().jvm().getRuntimeProperties().entrySet()) {
            String key = each.getKey();
            String value = each.getValue();
            if (key.startsWith("javax.net.ssl")) {
                defs.put(key, value);
            }
        }

        defs.put("java.awt.headless", "true");
        for (Map.Entry<String, String> each : defs.entrySet()) {
            params.getVMParametersList().defineProperty(each.getKey(), each.getValue());
        }

        boolean xmxSet = false;

        if (myVmOptions != null) {
            ParametersList mavenOptsList = new ParametersList();
            mavenOptsList.addParametersString(myVmOptions);

            for (String param : mavenOptsList.getParameters()) {
                if (param.startsWith("-Xmx")) {
                    xmxSet = true;
                }

                params.getVMParametersList().add(param);
            }
        }

        MavenServerManager manager = MavenServerManager.getInstance();
        String currentMavenVersion = manager.getCurrentMavenVersion();

        params.setJdk(myJdk);

        // better work MavenLeakDetector
        if (JavaSdkTypeUtil.isOfVersionOrHigher(myJdk, JavaSdkVersion.JDK_1_9)) {
            params.getVMParametersList().add("--add-opens");
            params.getVMParametersList().add("java.base/java.lang=ALL-UNNAMED");
        }

        params.getVMParametersList().addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_VERSION, currentMavenVersion);

        List<String> classPath = new ArrayList<>();

        classPath.add(ClassPathUtil.getJarPathForClass(RemoteServer.class)); // consulo-util-rmi
        classPath.add(ClassPathUtil.getJarPathForClass(Document.class)); // jdom
        ContainerUtil.addIfNotNull(classPath, ClassPathUtil.getJarPathForClass(Query.class));
        params.getClassPath().addAll(classPath);

        SimpleReference<String> mainClassRef = new SimpleReference<>(MavenServerManager.MAIN_CLASS_V3);
        params.getClassPath().addAllFiles(manager.collectClassPathAndLibsFolder(mainClassRef));
        params.setMainClass(mainClassRef.get());

        Platform platform = Platform.current();
        String embedderXmx = platform.jvm().getRuntimeProperty("idea.maven.embedder.xmx");
        if (embedderXmx != null) {
            params.getVMParametersList().add("-Xmx" + embedderXmx);
        }
        else {
            if (!xmxSet) {
                params.getVMParametersList().add("-Xmx512m");
            }
        }

        String mavenEmbedderDebugPort = myDebugPort != null
            ? String.valueOf(myDebugPort)
            : platform.jvm().getRuntimeProperty("idea.maven.embedder.debug.port");
        if (mavenEmbedderDebugPort != null) {
            params.getVMParametersList()
                .addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=" + mavenEmbedderDebugPort);
        }

        String mavenEmbedderParameters = platform.jvm().getRuntimeProperty("idea.maven.embedder.parameters");
        if (mavenEmbedderParameters != null) {
            params.getProgramParametersList().addParametersString(mavenEmbedderParameters);
        }

        String mavenEmbedderCliOptions = platform.jvm().getRuntimeProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
        if (mavenEmbedderCliOptions != null) {
            params.getVMParametersList()
                .addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS, mavenEmbedderCliOptions);
        }

        return params;
    }

    protected Map<String, String> getMavenOpts() {
        return MavenUtil.getPropertiesFromMavenOpts();
    }

    protected String getWorkingDirectory() {
        return ContainerPathManager.get().getBinPath();
    }

    @Override
    public ExecutionResult execute(Executor executor, ProgramRunner runner) throws ExecutionException {
        ProcessHandler processHandler = startProcess();
        return new DefaultExecutionResult(null, processHandler, AnAction.EMPTY_ARRAY);
    }

    @Override
    protected ProcessHandler startProcess() throws ExecutionException {
        OwnSimpleJavaParameters params = createJavaParameters();
        GeneralCommandLine commandLine = OwnJdkUtil.setupJVMCommandLine(params);

        return ProcessHandlerBuilder.create(commandLine)
            .shouldDestroyProcessRecursively(false)
            .silentReader()
            .build();
    }
}
