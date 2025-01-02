// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import consulo.build.ui.event.BuildEventFactory;
import consulo.localize.LocalizeValue;
import consulo.project.ui.notification.NotificationGroup;

public final class JavaBuildErrorNotification extends BuildErrorNotification {
    public static final NotificationGroup JAVA_COMPILER = NotificationGroup.balloonGroup("build.event.title.java.compiler", LocalizeValue.localizeTODO("Javac"));

    public JavaBuildErrorNotification(BuildEventFactory buildEventFactory) {
        super("java", "java", JAVA_COMPILER, buildEventFactory);
    }
}
