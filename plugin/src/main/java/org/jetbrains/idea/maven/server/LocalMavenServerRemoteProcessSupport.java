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

import consulo.content.bundle.Sdk;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.executor.Executor;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

public class LocalMavenServerRemoteProcessSupport extends AbstractMavenServerRemoteProcessSupport {
    public LocalMavenServerRemoteProcessSupport(Sdk jdk, @Nullable String vmOptions, @Nullable Project project, @Nullable Integer debugPort) {
        super(jdk, vmOptions, project, debugPort);
    }

    @Override
    protected RunProfileState getRunProfileState(Object target, Object configuration, Executor executor) {
        return new MavenServerCMDState(myJdk, myOptions, myDebugPort);
    }

    @Override
    public String type() {
        return "Local";
    }
}
