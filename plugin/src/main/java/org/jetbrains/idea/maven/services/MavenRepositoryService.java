/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.services;

import javax.annotation.Nonnull;

import consulo.maven.rt.server.common.model.MavenArtifactInfo;
import consulo.maven.rt.server.common.model.MavenRepositoryInfo;

import java.io.IOException;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public abstract class MavenRepositoryService {
    @Nonnull
    public abstract String getDisplayName();

    @Nonnull
    public abstract List<MavenRepositoryInfo> getRepositories(@Nonnull String url) throws IOException;

    @Nonnull
    public abstract List<MavenArtifactInfo> findArtifacts(@Nonnull String url, @Nonnull MavenArtifactInfo template) throws IOException;

    public final String toString() {
        return getDisplayName();
    }
}
