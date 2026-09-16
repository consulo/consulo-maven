/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing.configurers;

import consulo.module.Module;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.encoding.EncodingProjectManager;
import consulo.virtualFileSystem.pointer.VirtualFilePointer;
import consulo.virtualFileSystem.pointer.VirtualFilePointerManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class MavenEncodingConfigurer extends MavenModuleConfigurer {
    private final Map<VirtualFile, Charset> myCollected = new LinkedHashMap<>();

    @Override
    public void configure(@Nonnull MavenProject mavenProject, @Nonnull Project project, @Nullable Module module) {
        String encoding = mavenProject.getEncoding();
        if (encoding == null) {
            return;
        }

        try {
            myCollected.put(mavenProject.getDirectoryFile(), Charset.forName(encoding));
        }
        catch (UnsupportedCharsetException | IllegalCharsetNameException ignored) {
        }
    }

    /**
     * Applies every module's encoding in one call. {@code setEncoding} starts a modal reload per directory, and a modal
     * progress pumps the event queue - so a second queued call re-enters the encoding manager on the EDT and nests
     * another progress, which overflows the stack on a multi module import.
     */
    @Override
    public void afterConfigure(@Nonnull Project project) {
        if (myCollected.isEmpty()) {
            return;
        }

        Map<VirtualFile, Charset> collected = new LinkedHashMap<>(myCollected);
        myCollected.clear();

        EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(project);

        project.getUIAccess().execute(() -> {
            VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();

            Map<VirtualFilePointer, Charset> mapping = new LinkedHashMap<>(encodingManager.getAllPointersMappings());
            for (Map.Entry<VirtualFile, Charset> entry : collected.entrySet()) {
                mapping.put(pointerManager.create(entry.getKey(), project, null), entry.getValue());
            }

            encodingManager.setPointerMapping(mapping);
        });
    }
}
