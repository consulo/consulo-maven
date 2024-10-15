/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.indices;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.maven.rt.server.common.model.MavenRemoteRepository;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * {@link MavenRepositoryProvider} defines contract for additional repositories provisioning.
 *
 * @author Vladislav.Soroka
 * @since 10/25/13
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MavenRepositoryProvider {
    ExtensionPointName<MavenRepositoryProvider> EP_NAME = ExtensionPointName.create(MavenRepositoryProvider.class);

    @Nonnull
    Set<MavenRemoteRepository> getRemoteRepositories(@Nonnull Project project);
}
