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

// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import javax.annotation.Nonnull;

import consulo.language.psi.path.PathReference;
import consulo.xml.util.xml.Convert;
import consulo.xml.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.references.MavenDirectoryPathReferenceConverter;

/**
 * http://maven.apache.org/POM/4.0.0:Reporting interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Reporting documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomReporting extends MavenDomElement {
    /**
     * Returns the value of the excludeDefaults child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:excludeDefaults documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the excludeDefaults child.
     */
    @Nonnull
    GenericDomValue<Boolean> getExcludeDefaults();

    /**
     * Returns the value of the outputDirectory child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:outputDirectory documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the outputDirectory child.
     */
    @Nonnull
    @Convert(value = MavenDirectoryPathReferenceConverter.class, soft = true)
    GenericDomValue<PathReference> getOutputDirectory();

    /**
     * Returns the value of the plugins child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:plugins documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the plugins child.
     */
    @Nonnull
    MavenDomPlugins getPlugins();
}
