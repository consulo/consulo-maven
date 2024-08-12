/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.converters;

import java.util.Collection;
import java.util.Collections;

import consulo.language.psi.*;
import consulo.util.lang.function.Condition;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.idea.maven.dom.references.MavenPathReferenceConverter;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.xml.util.xml.ConvertContext;
import consulo.xml.util.xml.CustomReferenceConverter;
import consulo.xml.util.xml.GenericDomValue;
import consulo.xml.util.xml.ResolvingConverter;

public class MavenDependencySystemPathConverter extends ResolvingConverter<PsiFile> implements CustomReferenceConverter {
    @Override
    public PsiFile fromString(@Nullable @NonNls String s, ConvertContext context) {
        if (s == null) {
            return null;
        }
        VirtualFile f = LocalFileSystem.getInstance().findFileByPath(s);
        if (f == null) {
            return null;
        }
        return context.getPsiManager().findFile(f);
    }

    @Override
    public String toString(@Nullable PsiFile file, ConvertContext context) {
        if (file == null) {
            return null;
        }
        return file.getVirtualFile().getPath();
    }

    @Override
    @Nonnull
    public Collection<PsiFile> getVariants(ConvertContext context) {
        return Collections.emptyList();
    }

    @Override
    @Nonnull
    public PsiReference[] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
        return MavenPathReferenceConverter.createReferences(
            genericDomValue,
            element,
            item -> (item instanceof PsiDirectory) || item.getName().endsWith(".jar"),
            true
        );
    }
}
