// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.ui;

import consulo.annotation.component.ExtensionImpl;
import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.ui.ExternalSystemIconProvider;
import consulo.maven.icon.MavenIconGroup;
import consulo.ui.image.Image;
import org.jetbrains.idea.maven.utils.MavenUtil;

@ExtensionImpl
public class MavenIconProvider implements ExternalSystemIconProvider {
    @Override
    public ProjectSystemId getSystemId() {
        return MavenUtil.SYSTEM_ID;
    }

    @Override
    public Image getReloadIcon() {
        return MavenIconGroup.mavenloadchanges();
    }

    @Override
    public Image getProjectIcon() {
        return MavenIconGroup.mavenlogo();
    }
}
