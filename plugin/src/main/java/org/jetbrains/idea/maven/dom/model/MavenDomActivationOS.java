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

import jakarta.annotation.Nonnull;

import consulo.xml.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:ActivationOS interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:ActivationOS documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomActivationOS extends MavenDomElement {
    /**
     * Returns the value of the name child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:name documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the name child.
     */
    @Nonnull
    GenericDomValue<String> getName();

    /**
     * Returns the value of the family child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:family documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the family child.
     */
    @Nonnull
    GenericDomValue<String> getFamily();

    /**
     * Returns the value of the arch child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:arch documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the arch child.
     */
    @Nonnull
    GenericDomValue<String> getArch();

    /**
     * Returns the value of the version child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:version documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the version child.
     */
    @Nonnull
    GenericDomValue<String> getVersion();
}
