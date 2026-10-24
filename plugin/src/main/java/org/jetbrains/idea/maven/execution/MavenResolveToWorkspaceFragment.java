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
package org.jetbrains.idea.maven.execution;

import consulo.execution.configuration.fragment.TagRunConfigurationFragment;
import consulo.localize.LocalizeValue;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;

/**
 * Looks for the dependencies of a {@link MavenRunConfiguration} in the workspace before the local repository.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class MavenResolveToWorkspaceFragment implements TagRunConfigurationFragment<MavenRunConfiguration> {
    @Override
    public String getId() {
        return "maven.resolveToWorkspace";
    }

    @Override
    public LocalizeValue getName() {
        return MavenRunnerLocalize.mavenRunnerResolveWorkspaceArtifacts();
    }

    @Override
    public boolean isSet(MavenRunConfiguration target) {
        return target.getOptions().isResolveToWorkspace();
    }

    @Override
    public void set(MavenRunConfiguration target, boolean value) {
        target.getOptions().setResolveToWorkspace(value);
    }
}
