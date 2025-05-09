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
package org.jetbrains.idea.maven.dom.converters;

import consulo.localize.LocalizeValue;
import consulo.xml.util.xml.ConvertContext;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MavenModelVersionConverter extends MavenConstantListConverter {
    private static final String VERSION = "4.0.0";
    private static final List<String> VALUES = Collections.singletonList(VERSION);

    @Override
    protected Collection<String> getValues(@Nonnull ConvertContext context) {
        return VALUES;
    }

    @Nonnull
    @Override
    public LocalizeValue buildUnresolvedMessage(@Nullable String s, ConvertContext context) {
        return LocalizeValue.localizeTODO("Unsupported model version. Only version " + VERSION + " is supported.");
    }
}
