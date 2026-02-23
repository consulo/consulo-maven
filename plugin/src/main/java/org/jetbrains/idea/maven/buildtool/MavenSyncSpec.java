// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

public interface MavenSyncSpec {
    boolean forceReading();

    boolean resolveIncrementally();

    boolean isExplicit();

    static MavenSyncSpec incremental(String description) {
        return incremental(description, false);
    }

    static MavenSyncSpec incremental(String description, boolean explicit) {
        return new MavenSyncSpecImpl(true, explicit, description);
    }

    static MavenSyncSpec full(String description) {
        return full(description, false);
    }

    static MavenSyncSpec full(String description, boolean explicit) {
        return new MavenSyncSpecImpl(false, explicit, description);
    }

    /**
     * Returns the incremental mode for this sync spec.
     * @return 0 = INCREMENTAL, 1 = NON_INCREMENTAL, 2 = PARTIALLY_INCREMENTAL
     */
    static int getIncrementalMode(MavenSyncSpec spec) {
        boolean notForceReading = !spec.forceReading();
        boolean resolveIncrementally = spec.resolveIncrementally();

        if (notForceReading && resolveIncrementally) {
            return 0; // INCREMENTAL
        }
        if (!notForceReading && !resolveIncrementally) {
            return 1; // NON_INCREMENTAL
        }
        return 2; // PARTIALLY_INCREMENTAL
    }
}

