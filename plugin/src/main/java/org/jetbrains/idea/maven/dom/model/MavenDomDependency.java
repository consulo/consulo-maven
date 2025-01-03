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

import consulo.language.psi.PsiFile;
import consulo.xml.util.xml.Convert;
import consulo.xml.util.xml.GenericDomValue;
import consulo.xml.util.xml.Required;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyScopeConverter;
import org.jetbrains.idea.maven.dom.converters.MavenDependencySystemPathConverter;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyTypeConverter;

import jakarta.annotation.Nonnull;

/**
 * http://maven.apache.org/POM/4.0.0:Dependency interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Dependency documentation</h3>
 * 3.0.0+
 * </pre>
 */
public interface MavenDomDependency extends MavenDomElement, MavenDomArtifactCoordinates {
    @Required(value = false, nonEmpty = true)
    GenericDomValue<String> getVersion();

    /**
     * Returns the value of the type child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:type documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the type child.
     */
    @Nonnull
    @Required(value = false, nonEmpty = true)
    @Convert(MavenDependencyTypeConverter.class)
    GenericDomValue<String> getType();

    /**
     * Returns the value of the classifier child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:classifier documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the classifier child.
     */
    @Nonnull
    @Required(value = false, nonEmpty = true)
    GenericDomValue<String> getClassifier();

    /**
     * Returns the value of the scope child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:scope documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the scope child.
     */
    @Nonnull
    @Required(value = false, nonEmpty = true)
    @Convert(MavenDependencyScopeConverter.class)
    GenericDomValue<String> getScope();

    /**
     * Returns the value of the systemPath child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:systemPath documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the systemPath child.
     */
    @Nonnull
    @Required(value = false, nonEmpty = true)
    @Convert(MavenDependencySystemPathConverter.class)
    GenericDomValue<PsiFile> getSystemPath();

    /**
     * Returns the value of the exclusions child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:exclusions documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the exclusions child.
     */
    @Nonnull
    MavenDomExclusions getExclusions();

    /**
     * Returns the value of the optional child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:optional documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the optional child.
     */
    @Nonnull
    @Required(value = false, nonEmpty = true)
    GenericDomValue<Boolean> getOptional();
}
