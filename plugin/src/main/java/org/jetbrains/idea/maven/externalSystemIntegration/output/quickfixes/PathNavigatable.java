// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import consulo.fileEditor.FileEditorManager;
import consulo.navigation.Navigatable;
import consulo.navigation.OpenFileDescriptor;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.nio.file.Path;

public class PathNavigatable implements Navigatable {
    private final Project myProject;
    private final Path myPath;
    private final int myOffset;
    private volatile OpenFileDescriptor myDescriptor;
    private volatile boolean descriptorInitialized = false;

    public PathNavigatable(@Nonnull Project project, @Nonnull Path path, int offset) {
        this.myProject = project;
        this.myPath = path;
        this.myOffset = offset;
    }

    @Override
    public void navigate(boolean requestFocus) {
        OpenFileDescriptor descriptor = getDescriptor();
        if (descriptor != null) {
            descriptor.navigate(requestFocus);
        }
    }

    @Nullable
    private OpenFileDescriptor getDescriptor() {
        if (!descriptorInitialized) {
            synchronized (this) {
                if (!descriptorInitialized) {
                    myDescriptor = createFileDescriptor();
                    descriptorInitialized = true;
                }
            }
        }
        return myDescriptor;
    }

    @Nullable
    private OpenFileDescriptor createFileDescriptor() {
        VirtualFile vFile = VirtualFileUtil.findFile(myPath);
        if (vFile == null) {
            return null;
        }
        return new OpenFileDescriptor(myProject, vFile, myOffset);
    }

    @Override
    public boolean canNavigate() {
        OpenFileDescriptor descriptor = getDescriptor();
        return descriptor != null && descriptor.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        OpenFileDescriptor descriptor = getDescriptor();
        return descriptor != null && descriptor.canNavigateToSource();
    }
}
