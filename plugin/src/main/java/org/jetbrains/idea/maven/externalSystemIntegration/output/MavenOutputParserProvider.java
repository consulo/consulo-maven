// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import consulo.annotation.component.ExtensionImpl;
import consulo.build.ui.event.BuildEventFactory;
import consulo.build.ui.output.BuildOutputParser;
import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.externalSystem.service.execution.ExternalSystemOutputParserProvider;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.List;
import java.util.function.Function;

@ExtensionImpl
public class MavenOutputParserProvider implements ExternalSystemOutputParserProvider {
    private final BuildEventFactory myBuildEventFactory;

    @Inject
    public MavenOutputParserProvider(BuildEventFactory buildEventFactory) {
        myBuildEventFactory = buildEventFactory;
    }

    @Override
    public ProjectSystemId getExternalSystemId() {
        return MavenUtil.SYSTEM_ID;
    }

    @Override
    public List<BuildOutputParser> getBuildOutputParsers(@Nonnull ExternalSystemTaskId taskId) {
        throw new UnsupportedOperationException();
    }

    public static MavenLogOutputParser createMavenOutputParser(@Nonnull BuildEventFactory buildEventFactory,
                                                               @Nonnull MavenRunConfiguration runConfiguration,
                                                               @Nonnull ExternalSystemTaskId taskId,
                                                               @Nonnull Function<String, String> targetFileMapper,
                                                               boolean useWrapperedLogging) {
        return new MavenLogOutputParser(
            buildEventFactory,
            runConfiguration,
            taskId,
            targetFileMapper,
            MavenLoggedEventParser.EP_NAME.getExtensionList(),
            useWrapperedLogging
        );
    }
}
