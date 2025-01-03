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

import org.jetbrains.idea.maven.dom.MavenDomElement;

public interface MavenDomProjectModelBase extends MavenDomElement {
    @Nonnull
    MavenDomBuildBase getBuild();

    @Nonnull
    MavenDomModules getModules();

    @Nonnull
    MavenDomRepositories getRepositories();

    @Nonnull
    MavenDomPluginRepositories getPluginRepositories();

    @Nonnull
    MavenDomDependencies getDependencies();

    @Nonnull
    MavenDomReports getReports();

    @Nonnull
    MavenDomReporting getReporting();

    @Nonnull
    MavenDomDependencyManagement getDependencyManagement();

    @Nonnull
    MavenDomDistributionManagement getDistributionManagement();

    @Nonnull
    MavenDomProperties getProperties();
}