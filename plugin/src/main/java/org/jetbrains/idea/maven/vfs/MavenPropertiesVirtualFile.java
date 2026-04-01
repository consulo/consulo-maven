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
package org.jetbrains.idea.maven.vfs;

import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.BaseVirtualFile;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileSystem;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public class MavenPropertiesVirtualFile extends BaseVirtualFile {
    private final String myPath;
    private final VirtualFileSystem myFS;
    private final byte[] myContent;

    public MavenPropertiesVirtualFile(String path, Properties properties, VirtualFileSystem FS) {
        myPath = path;
        myFS = FS;

        myContent = createContent(properties);
    }

    private byte[] createContent(Properties properties) {
        StringBuilder builder = new StringBuilder();
        TreeSet<String> sortedKeys = new TreeSet<String>((Set)properties.keySet());
        for (String each : sortedKeys) {
            builder.append(StringUtil.escapeProperty(each, true));
            builder.append("=");
            builder.append(StringUtil.escapeProperty(properties.getProperty(each), false));
            builder.append("\n");
        }
        return builder.toString().getBytes();
    }

    @Override
    public String getName() {
        return myPath;
    }

    @Override
    public VirtualFileSystem getFileSystem() {
        return myFS;
    }

    @Override
    public String getPath() {
        return myPath;
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public @Nullable VirtualFile getParent() {
        return null;
    }

    @Override
    public VirtualFile @Nullable [] getChildren() {
        return null;
    }

    @Override
    public byte[] contentsToByteArray() throws IOException {
        if (myContent == null) {
            throw new IOException();
        }
        return myContent;
    }

    @Override
    public long getTimeStamp() {
        return -1;
    }

    @Override
    public long getModificationStamp() {
        return myContent.hashCode();
    }

    @Override
    public long getLength() {
        return myContent.length;
    }

    @Override
    public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return VirtualFileUtil.byteStreamSkippingBOM(myContent, this);
    }

    @Override
    public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
        throw new UnsupportedOperationException();
    }
}
