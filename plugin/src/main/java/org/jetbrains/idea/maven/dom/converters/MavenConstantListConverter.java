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
import consulo.util.lang.StringUtil;
import consulo.xml.util.xml.ConvertContext;
import consulo.xml.util.xml.ResolvingConverter;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import java.util.Collection;

public abstract class MavenConstantListConverter extends ResolvingConverter<String> {
    private boolean myStrict;

    protected MavenConstantListConverter() {
        this(true);
    }

    protected MavenConstantListConverter(boolean strict) {
        myStrict = strict;
    }

    @Override
    public String fromString(@Nullable String s, ConvertContext context) {
        if (!myStrict) {
            return s;
        }
        return getValues(context).contains(s) ? s : null;
    }

    @Override
    public String toString(@Nullable String s, ConvertContext context) {
        return s;
    }

    @Nonnull
    @Override
    public Collection<String> getVariants(ConvertContext context) {
        return getValues(context);
    }

    protected abstract Collection<String> getValues(@Nonnull ConvertContext context);

    @Nonnull
    @Override
    public LocalizeValue buildUnresolvedMessage(@Nullable String s, ConvertContext context) {
        return LocalizeValue.localizeTODO(
            "<html>Specified value is not acceptable here.<br>Acceptable values: " +
                StringUtil.join(getValues(context), ", ") + "</html>"
        );
    }
}