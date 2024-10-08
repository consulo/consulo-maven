// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import java.io.IOException;

import org.jetbrains.idea.maven.server.MavenIndicesProcessor;
import consulo.maven.rt.server.common.server.MavenServerIndexerException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

/**
 * @author ibessonov
 */
interface NotNexusIndexer {
    void processArtifacts(MavenProgressIndicator progress, MavenIndicesProcessor processor) throws IOException, MavenServerIndexerException;
}
