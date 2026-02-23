package org.jetbrains.idea.maven.buildtool;

import consulo.project.ui.notification.NotificationGroup;
import org.jetbrains.idea.maven.localize.MavenSyncLocalize;

/**
 * @author VISTALL
 * @since 2026-02-23
 */
public class MavenBuildNotification {
    public static final NotificationGroup BUILD_ERROR =
        NotificationGroup.logOnlyGroup("MavenBuildError", MavenSyncLocalize.buildEventTitleError());

    public static final NotificationGroup COMPILER =
        NotificationGroup.logOnlyGroup("MavenCompiler", MavenSyncLocalize.mavenSyncGroupCompiler());

    public static final NotificationGroup BUILD_WARN =
        NotificationGroup.logOnlyGroup("MavenBuildWarning", MavenSyncLocalize.mavenSyncGroupWarning());

    public static final NotificationGroup BUILD_INTERNAL_ERROR =
        NotificationGroup.logOnlyGroup("MavenInternalBuildError", MavenSyncLocalize.buildEventTitleInternalServerError());
}
