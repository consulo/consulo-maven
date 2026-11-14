/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.idea.maven.server.platform;

import consulo.application.platform.PlatformTransfer;
import consulo.content.bundle.Sdk;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.executor.Executor;
import consulo.java.execution.OwnSimpleJavaParameters;
import consulo.platform.ForwardedPort;
import consulo.platform.Platform;
import consulo.platform.PlatformServerSocket;
import consulo.platform.PlatformSocket;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import org.jetbrains.idea.maven.server.AbstractMavenServerRemoteProcessSupport;
import org.jetbrains.idea.maven.server.MavenServerCMDState;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The maven server on the machine of the project (JetBrains: {@code EelMavenServerRemoteProcessSupport}): the classpath is
 * transferred to that machine, the ports of the server are forwarded to the same ports of the IDE machine, the port of the
 * objects the IDE exports (the dead hand, the listeners of the server) is published back on that machine.
 */
public class PlatformMavenServerRemoteProcessSupport extends AbstractMavenServerRemoteProcessSupport {
    private static final String LOOPBACK = "127.0.0.1";

    private final Platform myPlatform;
    private final Set<AutoCloseable> myPublished = ConcurrentHashMap.newKeySet();
    private final Set<Integer> myCallbackPorts = ConcurrentHashMap.newKeySet();

    public PlatformMavenServerRemoteProcessSupport(Platform platform,
                                                   Sdk jdk,
                                                   @Nullable String vmOptions,
                                                   Project project,
                                                   @Nullable Integer debugPort) {
        super(jdk, vmOptions, project, debugPort);
        myPlatform = platform;
    }

    @Override
    protected RunProfileState getRunProfileState(Object target, Object configuration, Executor executor) {
        return new PlatformMavenCmdState(myJdk, myOptions, myDebugPort);
    }

    @Override
    protected String getRemoteHost() {
        return LOOPBACK;
    }

    @Override
    protected int publishPort(int port, Object target) throws IOException {
        ForwardedPort forwardedPort = myPlatform.net().forward(LOOPBACK, port, port);
        myPublished.add(forwardedPort);
        return forwardedPort.port();
    }

    @Override
    protected void publishCallbackPort(int port, Object target) throws IOException {
        if (!myCallbackPorts.add(port)) {
            return;
        }
        PlatformServerSocket serverSocket = myPlatform.net().bind(LOOPBACK, port);
        myPublished.add(serverSocket);
        Thread.ofVirtual().name("Maven server callbacks of port " + port).start(() -> acceptCallbacks(serverSocket, port));
    }

    @Override
    public String type() {
        return "Platform";
    }

    private void unpublish() {
        for (AutoCloseable closeable : myPublished) {
            try {
                closeable.close();
            }
            catch (Exception ignored) {
            }
        }
        myPublished.clear();
        myCallbackPorts.clear();
    }

    /**
     * A connection of the server to a port published back is piped to the same port of the IDE machine.
     */
    private static void acceptCallbacks(PlatformServerSocket serverSocket, int port) {
        while (true) {
            PlatformSocket remote;
            try {
                remote = serverSocket.accept();
            }
            catch (IOException e) {
                return;
            }
            Thread.ofVirtual().start(() -> {
                try (remote; Socket local = new Socket(InetAddress.getLoopbackAddress(), port)) {
                    Thread toIde = Thread.ofVirtual().start(() -> pipe(remote::getInputStream, local::getOutputStream, local::shutdownOutput));
                    pipe(local::getInputStream, remote::getOutputStream, () -> remote.getOutputStream().close());
                    toIde.join();
                }
                catch (Exception e) {
                    MavenLog.LOG.debug(e);
                }
            });
        }
    }

    private interface IOSupplier<T> {
        T get() throws IOException;
    }

    private interface IORunnable {
        void run() throws IOException;
    }

    private static void pipe(IOSupplier<InputStream> from, IOSupplier<OutputStream> to, IORunnable onEnd) {
        try {
            from.get().transferTo(to.get());
        }
        catch (IOException ignored) {
        }
        finally {
            try {
                onEnd.run();
            }
            catch (IOException ignored) {
            }
        }
    }

    /**
     * {@code java} of the bundle of the machine, with the classpath transferred to it (JetBrains: {@code EelMavenCmdState}).
     */
    private class PlatformMavenCmdState extends MavenServerCMDState {
        PlatformMavenCmdState(Sdk jdk, @Nullable String vmOptions, @Nullable Integer debugPort) {
            super(jdk, vmOptions, debugPort);
        }

        @Override
        protected String getWorkingDirectory() {
            return myPlatform.user().homePath().toString();
        }

        @Override
        protected Map<String, String> getMavenOpts() {
            // the MAVEN_OPTS of the IDE machine are not the ones of the machine of the server
            return new HashMap<>();
        }

        @Override
        protected ProcessHandler startProcess() throws ExecutionException {
            OwnSimpleJavaParameters params = createJavaParameters();

            GeneralCommandLine commandLine = new GeneralCommandLine();
            commandLine.withPlatform(myPlatform);
            Path home = myJdk.getHomeNioPath();
            commandLine.withExecutablePath(home.resolve("bin").resolve(myPlatform.os().isWindows() ? "java.exe" : "java"));
            commandLine.withWorkingDirectory(myPlatform.fs().getPath(getWorkingDirectory()));

            for (String parameter : params.getVMParametersList().getParameters()) {
                // the trust stores of the IDE machine are not on the machine of the server
                if (!parameter.startsWith("-Djavax.net.ssl")) {
                    commandLine.addParameter(parameter);
                }
            }

            PlatformTransfer transfer = PlatformTransfer.getInstance();
            List<String> classPath = new ArrayList<>();
            try {
                for (String entry : params.getClassPath().getPathList()) {
                    classPath.add(transfer.transfer(Path.of(entry).toRealPath(), myPlatform).toString());
                }
            }
            catch (IOException e) {
                throw new ExecutionException("Cannot transfer the maven server to " + myPlatform.getName() + ": " + e.getMessage(), e);
            }
            commandLine.addParameter("-classpath");
            commandLine.addParameter(String.join(myPlatform.fs().getPathListSeparator(), classPath));
            commandLine.addParameter(params.getMainClass());
            commandLine.addParameters(params.getProgramParametersList().getParameters());

            ProcessHandler handler = ProcessHandlerBuilder.create(commandLine)
                .shouldDestroyProcessRecursively(false)
                .silentReader()
                .build();
            handler.addProcessListener(new ProcessListener() {
                @Override
                public void processTerminated(ProcessEvent event) {
                    unpublish();
                }
            });
            return handler;
        }
    }
}
