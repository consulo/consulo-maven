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

import consulo.content.bundle.Sdk;
import consulo.maven.rt.server.common.server.MavenServer;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.rmi.RemoteException;

/**
 * The maven server of the machine of a project - started by the {@link MavenRemoteProcessSupportFactory} applicable to
 * the project.
 */
public class MavenServerConnector extends RemoteObjectWrapper<MavenServer> {
    private final Project myProject;
    private final Sdk myJdk;
    private final @Nullable String myVmOptions;
    private MavenRemoteProcessSupportFactory.@Nullable MavenRemoteProcessSupport mySupport;

    public MavenServerConnector(Project project, Sdk jdk, @Nullable String vmOptions) {
        super(null);
        myProject = project;
        myJdk = jdk;
        myVmOptions = vmOptions;
    }

    @Override
    protected synchronized MavenServer create() throws RemoteException {
        if (mySupport == null) {
            mySupport = MavenRemoteProcessSupportFactory.forProject(myProject).create(myJdk, myVmOptions, myProject, null);
        }
        MavenServer result;
        try {
            result = mySupport.acquire(this, "");
        }
        catch (Exception e) {
            throw new RemoteException("Cannot start maven service", e);
        }
        MavenServerManager.getInstance().attachListeners(result);
        return result;
    }

    public synchronized void shutdown(boolean wait) {
        if (mySupport != null) {
            mySupport.stopAll(wait);
        }
        cleanup();
    }
}
