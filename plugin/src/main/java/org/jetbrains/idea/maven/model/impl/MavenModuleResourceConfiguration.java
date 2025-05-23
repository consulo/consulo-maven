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
package org.jetbrains.idea.maven.model.impl;

import consulo.util.collection.Sets;
import consulo.util.io.FileUtil;
import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.util.xml.serializer.annotation.MapAnnotation;
import consulo.util.xml.serializer.annotation.OptionTag;
import consulo.util.xml.serializer.annotation.Tag;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 * @since 2012-10-20
 */
public class MavenModuleResourceConfiguration {
    @Nonnull
    @Tag("id")
    public MavenIdBean id;

    @Nullable
    @Tag("parentId")
    public MavenIdBean parentId;

    @Nonnull
    @Tag("directory")
    public String directory;

    @Nonnull
    @Tag("delimiters-pattern")
    public String delimitersPattern;

    @Tag("model-map")
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    public Map<String, String> modelMap = new HashMap<>();

    @Tag("properties")
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    public Map<String, String> properties = new HashMap<>();

    @Tag("filtering-excluded-extensions")
    @AbstractCollection(surroundWithTag = false, elementTag = "extension")
    public Set<String> filteringExclusions = Sets.newHashSet(FileUtil.PATH_HASHING_STRATEGY);

    @OptionTag
    public String escapeString = MavenProjectConfiguration.DEFAULT_ESCAPE_STRING;

    @OptionTag
    public boolean escapeWindowsPaths = true;

    @Tag("resources")
    @AbstractCollection(surroundWithTag = false, elementTag = "resource")
    public List<ResourceRootConfiguration> resources = new ArrayList<>();

    @Tag("test-resources")
    @AbstractCollection(surroundWithTag = false, elementTag = "resource")
    public List<ResourceRootConfiguration> testResources = new ArrayList<>();

    public Set<String> getFilteringExcludedExtensions() {
        if (filteringExclusions.isEmpty()) {
            return MavenProjectConfiguration.DEFAULT_FILTERING_EXCLUDED_EXTENSIONS;
        }
        final Set<String> result = Sets.newHashSet(FileUtil.PATH_HASHING_STRATEGY);
        result.addAll(MavenProjectConfiguration.DEFAULT_FILTERING_EXCLUDED_EXTENSIONS);
        result.addAll(filteringExclusions);
        return Collections.unmodifiableSet(result);
    }

    public int computeConfigurationHash(boolean forTestResources) {
        int result = id.hashCode();
        result = 31 * result + (parentId != null ? parentId.hashCode() : 0);
        result = 31 * result + directory.hashCode();
        result = 31 * result + delimitersPattern.hashCode();
        result = 31 * result + modelMap.hashCode();
        result = 31 * result + properties.hashCode();
        result = 31 * result + filteringExclusions.hashCode();
        result = 31 * result + (escapeString != null ? escapeString.hashCode() : 0);
        result = 31 * result + (escapeWindowsPaths ? 1 : 0);

        final List<ResourceRootConfiguration> _resources = forTestResources ? testResources : resources;
        result = 31 * result;
        for (ResourceRootConfiguration resource : _resources) {
            result += resource.computeConfigurationHash();
        }
        return result;
    }
}



