// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import consulo.build.ui.event.BuildEventFactory;
import consulo.build.ui.event.DerivedResult;
import consulo.build.ui.event.EventResult;
import consulo.build.ui.event.FailureResult;
import jakarta.annotation.Nonnull;

public class MavenTaskFailedResultImpl implements DerivedResult {
    private final String myError;
    private final BuildEventFactory myBuildEventFactory;

    public MavenTaskFailedResultImpl(String error, BuildEventFactory buildEventFactory) {
        myError = error;
        myBuildEventFactory = buildEventFactory;
    }

    @Nonnull
    @Override
    public FailureResult createFailureResult() {
        return myBuildEventFactory.createFailureResult();
    }

    @Nonnull
    @Override
    public EventResult createDefaultResult() {
        return myBuildEventFactory.createFailureResult(myError);
    }
}
