// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import consulo.application.util.UserHomeFileUtil;
import consulo.maven.icon.MavenIconGroup;
import consulo.navigation.Navigatable;
import consulo.ui.ex.tree.PresentationData;
import consulo.ui.image.Image;
import consulo.util.io.FileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class RepositoryNode extends MavenSimpleNode {
    private final String myId;
    private final String myUrl;
    private final boolean myLocal;

    RepositoryNode(MavenProjectsStructure structure, RepositoriesNode parent, String id, String url, boolean local) {
        super(structure, parent);
        myId = id;
        myUrl = url;
        myLocal = local;
        PresentationData presentation = getTemplatePresentation();
        presentation.setIcon(getDefaultIcon());
        setNameAndTooltip(presentation, myId, null, myLocal ? getPresentablePath(myUrl) : myUrl);
    }

    private static String getPresentablePath(String path) {
        return UserHomeFileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path.trim()), false);
    }

    @Nonnull
    private Image getDefaultIcon() {
        return myLocal ? MavenIconGroup.mavenlogo() : MavenIconGroup.plugingoal();
    }

    @Override
    protected void update(@Nonnull PresentationData presentation) {
        setNameAndTooltip(presentation, myId, null, myLocal ? getPresentablePath(myUrl) : myUrl);
    }

    @Override
    public String getName() {
        return myId;
    }

    @Override
    public String getMenuId() {
        return "Maven.RepositoryMenu";
    }

    String getId() {
        return myId;
    }

    String getUrl() {
        return myUrl;
    }

    boolean isLocal() {
        return myLocal;
    }

    @Override
    @Nullable
    public Navigatable getNavigatable() {
        return null;
    }
}
