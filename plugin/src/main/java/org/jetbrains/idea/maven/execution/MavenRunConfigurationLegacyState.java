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

import consulo.util.xml.serializer.annotation.Tag;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jspecify.annotations.Nullable;

/**
 * The {@code <MavenSettings>} element a maven run configuration wrote before the fragment model.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@Tag("MavenSettings")
public class MavenRunConfigurationLegacyState {
    public static final String TAG = "MavenSettings";

    public @Nullable MavenGeneralSettings myGeneralSettings;
    public @Nullable MavenRunnerSettings myRunnerSettings;
    public @Nullable MavenRunnerParameters myRunnerParameters;
}
