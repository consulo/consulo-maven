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
import com.intellij.util.xml.Required;
import com.intellij.util.xml.SubTag;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenModelVersionConverter;
import org.jetbrains.idea.maven.dom.converters.MavenPackagingConverter;
import org.jetbrains.idea.maven.dom.converters.MavenUrlConverter;

/**
 * http://maven.apache.org/POM/4.0.0:Model interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Model documentation</h3>
 * 3.0.0+
 * </pre>
 */
public interface MavenDomProjectModel extends MavenDomElement, MavenDomProjectModelBase, MavenDomArtifactCoordinates {
  /**
   * Returns the value of the parent child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:parent documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the parent child.
   */
  @Nonnull
  @SubTag("parent")
  MavenDomParent getMavenParent();

  /**
   * Returns the value of the modelVersion child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:modelVersion documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the modelVersion child.
   */
  @Nonnull
  @Required
  @Convert(MavenModelVersionConverter.class)
  GenericDomValue<String> getModelVersion();

  /**
   * Returns the value of the groupId child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:groupId documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the groupId child.
   */
  @Nonnull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getGroupId();

  /**
   * Returns the value of the artifactId child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:artifactId documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the artifactId child.
   */
  @Nonnull
  @Required
  GenericDomValue<String> getArtifactId();

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
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getVersion();

  /**
   * Returns the value of the packaging child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:packaging documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the packaging child.
   */
  @Nonnull
  @Convert(MavenPackagingConverter.class)
  GenericDomValue<String> getPackaging();

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
   * Returns the value of the description child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:description documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the description child.
   */
  @Nonnull
  GenericDomValue<String> getDescription();

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
   * Returns the value of the prerequisites child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:prerequisites documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the prerequisites child.
   */
  @Nonnull
  MavenDomPrerequisites getPrerequisites();

  /**
   * Returns the value of the issueManagement child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:issueManagement documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the issueManagement child.
   */
  @Nonnull
  MavenDomIssueManagement getIssueManagement();

  /**
   * Returns the value of the ciManagement child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:ciManagement documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the ciManagement child.
   */
  @Nonnull
  MavenDomCiManagement getCiManagement();

  /**
   * Returns the value of the inceptionYear child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:inceptionYear documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the inceptionYear child.
   */
  @Nonnull
  GenericDomValue<String> getInceptionYear();

  /**
   * Returns the value of the mailingLists child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:mailingLists documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the mailingLists child.
   */
  @Nonnull
  MavenDomMailingLists getMailingLists();

  /**
   * Returns the value of the developers child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:developers documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the developers child.
   */
  @Nonnull
  MavenDomDevelopers getDevelopers();

  /**
   * Returns the value of the contributors child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:contributors documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the contributors child.
   */
  @Nonnull
  MavenDomContributors getContributors();

  /**
   * Returns the value of the licenses child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:licenses documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the licenses child.
   */
  @Nonnull
  MavenDomLicenses getLicenses();

  /**
   * Returns the value of the scm child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:scm documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the scm child.
   */
  @Nonnull
  MavenDomScm getScm();

  /**
   * Returns the value of the organization child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:organization documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the organization child.
   */
  @Nonnull
  MavenDomOrganization getOrganization();

  @Nonnull
  MavenDomBuild getBuild();

  /**
   * Returns the value of the profiles child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:profiles documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the profiles child.
   */
  @Nonnull
  MavenDomProfiles getProfiles();
}
