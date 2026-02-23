// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.output;

import consulo.build.ui.event.BuildEvent;
import consulo.build.ui.output.BuildOutputInstantReader;
import consulo.build.ui.output.BuildOutputParser;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.MavenImportLoggedEventParser;

import java.util.function.Consumer;

public class MavenImportOutputParser implements BuildOutputParser {

    private final Project myProject;

    public MavenImportOutputParser(@Nonnull Project project) {
        myProject = project;
    }

    @Override
    public boolean parse(@Nonnull String line,
                         @Nullable BuildOutputInstantReader reader,
                         @Nonnull Consumer<? super BuildEvent> messageConsumer) {
        if (StringUtil.isEmptyOrSpaces(line)) {
            return false;
        }

        for (MavenImportLoggedEventParser event : MavenImportLoggedEventParser.EP_NAME.getExtensionList()) {
            if (event.processLogLine(myProject, line, reader, messageConsumer)) {
                return true;
            }
        }

        return false;
    }
}
