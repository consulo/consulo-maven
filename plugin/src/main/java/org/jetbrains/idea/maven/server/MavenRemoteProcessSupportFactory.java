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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.content.bundle.Sdk;
import consulo.ide.impl.idea.execution.rmi.RemoteProcessSupport;
import consulo.maven.rt.server.common.server.MavenServer;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Extension point for create maven remote process.
 * And to interact with the process and handling its callbacks.
 * {@link MavenRemoteProcessSupport}
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MavenRemoteProcessSupportFactory {
    /**
     * create remote procces.
     *
     * @param jdk       - jdk data.
     * @param vmOptions - vm command line options.
     * @param project   - idea project.
     * @param debugPort - port for remote debugging.
     * @return remote maven process.
     */
    MavenRemoteProcessSupport create(Sdk jdk, @Nullable String vmOptions, Project project, @Nullable Integer debugPort);

    static MavenRemoteProcessSupportFactory forProject(Project project) {
        MavenRemoteProcessSupportFactory applicable = Application.get()
            .getExtensionPoint(MavenRemoteProcessSupportFactory.class)
            .findFirstSafe(factory -> factory.isApplicable(project));
        if (applicable == null) {
            return new LocalMavenRemoteProcessSupportFactory();
        }
        return applicable;
    }

    boolean isApplicable(Project project);

    abstract class MavenRemoteProcessSupport extends RemoteProcessSupport<Object, MavenServer, Object> {
        public MavenRemoteProcessSupport(Class<MavenServer> valueClass) {
            super(valueClass);
        }

        public abstract String type();
    }
}
