package org.jetbrains.idea.maven.buildtool;

class MavenSyncSpecImpl implements MavenSyncSpec {
    private final boolean incremental;
    private final boolean explicit;
    private final String description;

    MavenSyncSpecImpl(boolean incremental, boolean explicit, String description) {
        this.incremental = incremental;
        this.explicit = explicit;
        this.description = description;
    }

    @Override
    public boolean forceReading() {
        return !incremental;
    }

    @Override
    public boolean resolveIncrementally() {
        return incremental;
    }

    @Override
    public boolean isExplicit() {
        return explicit;
    }

    @Override
    public String toString() {
        return "incremental=" + incremental + ", " + description;
    }
}
