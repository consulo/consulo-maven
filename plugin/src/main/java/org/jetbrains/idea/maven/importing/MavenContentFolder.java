package org.jetbrains.idea.maven.importing;

import consulo.annotation.UsedInPlugin;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2025-10-04
 */
public interface MavenContentFolder {
    @Nonnull
    @UsedInPlugin
    MavenContentFolder setGenerated();
}
