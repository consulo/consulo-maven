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

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenUrlConverter;

/**
 * http://maven.apache.org/POM/4.0.0:License interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:License documentation</h3>
 * 3.0.0+
 * </pre>
 */
public interface MavenDomLicense extends MavenDomElement {

  /**
   * Returns the value of the name child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:name documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the name child.
   */
  @Nonnull
  GenericDomValue<String> getName();

  /**
   * Returns the value of the url child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:url documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the url child.
   */
  @Nonnull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getUrl();

  /**
   * Returns the value of the distribution child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:distribution documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the distribution child.
   */
  @Nonnull
  GenericDomValue<String> getDistribution();

  /**
   * Returns the value of the comments child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:comments documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the comments child.
   */
  @Nonnull
  GenericDomValue<String> getComments();
}
