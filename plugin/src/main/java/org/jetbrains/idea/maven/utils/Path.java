/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.utils;

import consulo.util.io.FileUtil;
import consulo.util.io.PathUtil;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import javax.annotation.Nonnull;

public class Path {
    private final String path;

    public Path(@Nonnull String path) {
        path = PathUtil.getCanonicalPath(path);
        path = FileUtil.toSystemIndependentName(path);
        this.path = path;
    }

    @Nonnull
    public String getPath() {
        return path;
    }

    public Url toUrl() {
        return new Url(VirtualFileUtil.pathToUrl(path));
    }

    @Override
    public String toString() {
        return path;
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        return (o instanceof Path) && path.equals(((Path)o).path);
    }
}
