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
package org.jetbrains.idea.maven.dom.references;

import com.google.common.collect.ImmutableMap;
import consulo.java.language.module.util.JavaClassNames;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class MavenModelClassesProperties {

    private static final Map<String, Map<String, String>> PROPERTIES_MAP;

    public static final String MAVEN_PROJECT_CLASS = "org.apache.maven.project.MavenProject";
    public static final String MAVEN_MODEL_CLASS = "org.apache.maven.model.Model";

    static {
        Map<String, Map<String, String>> res = new HashMap<>();

        res.put(MAVEN_PROJECT_CLASS, ImmutableMap.<String, String>builder()
            .put("parentFile", "java.io.File")
            .put("artifact", "org.apache.maven.artifact.Artifact")
            .put("model", MAVEN_MODEL_CLASS)
            .put("parent", MAVEN_PROJECT_CLASS)
            .put("file", "java.io.File")
            .put("dependencies", JavaClassNames.JAVA_UTIL_LIST)
            .put("compileSourceRoots", JavaClassNames.JAVA_UTIL_LIST)
            .put("scriptSourceRoots", JavaClassNames.JAVA_UTIL_LIST)
            .put("testCompileSourceRoots", JavaClassNames.JAVA_UTIL_LIST)
            .put("compileClasspathElements", JavaClassNames.JAVA_UTIL_LIST)
            .put("compileArtifacts", JavaClassNames.JAVA_UTIL_LIST)
            .put("compileDependencies", JavaClassNames.JAVA_UTIL_LIST)
            .put("testClasspathElements", JavaClassNames.JAVA_UTIL_LIST)
            .put("testArtifacts", JavaClassNames.JAVA_UTIL_LIST)
            .put("testDependencies", JavaClassNames.JAVA_UTIL_LIST)
            .put("runtimeClasspathElements", JavaClassNames.JAVA_UTIL_LIST)
            .put("runtimeArtifacts", JavaClassNames.JAVA_UTIL_LIST)
            .put("runtimeDependencies", JavaClassNames.JAVA_UTIL_LIST)
            .put("systemClasspathElements", JavaClassNames.JAVA_UTIL_LIST)
            .put("systemArtifacts", JavaClassNames.JAVA_UTIL_LIST)
            .put("systemDependencies", JavaClassNames.JAVA_UTIL_LIST)
            .put("modelVersion", JavaClassNames.JAVA_LANG_STRING)
            .put("id", JavaClassNames.JAVA_LANG_STRING)
            .put("groupId", JavaClassNames.JAVA_LANG_STRING)
            .put("artifactId", JavaClassNames.JAVA_LANG_STRING)
            .put("version", JavaClassNames.JAVA_LANG_STRING)
            .put("packaging", JavaClassNames.JAVA_LANG_STRING)
            .put("name", JavaClassNames.JAVA_LANG_STRING)
            .put("inceptionYear", JavaClassNames.JAVA_LANG_STRING)
            .put("url", JavaClassNames.JAVA_LANG_STRING)
            .put("prerequisites", "org.apache.maven.model.Prerequisites")
            .put("issueManagement", "org.apache.maven.model.IssueManagement")
            .put("ciManagement", "org.apache.maven.model.CiManagement")
            .put("description", JavaClassNames.JAVA_LANG_STRING)
            .put("organization", "org.apache.maven.model.Organization")
            .put("scm", "org.apache.maven.model.Scm")
            .put("mailingLists", JavaClassNames.JAVA_UTIL_LIST)
            .put("developers", JavaClassNames.JAVA_UTIL_LIST)
            .put("contributors", JavaClassNames.JAVA_UTIL_LIST)
            .put("build", "org.apache.maven.model.Build")

            .put("resources", JavaClassNames.JAVA_UTIL_LIST)
            .put("testResources", JavaClassNames.JAVA_UTIL_LIST)
            .put("reporting", "org.apache.maven.model.Reporting")
            .put("licenses", JavaClassNames.JAVA_UTIL_LIST)
            .put("artifacts", JavaClassNames.JAVA_UTIL_LIST)
            .put("artifactMap", JavaClassNames.JAVA_UTIL_LIST)
            .put("pluginArtifacts", JavaClassNames.JAVA_UTIL_LIST)
            .put("pluginArtifactMap", JavaClassNames.JAVA_UTIL_LIST)
            .put("reportArtifacts", JavaClassNames.JAVA_UTIL_LIST)
            .put("reportArtifactMap", JavaClassNames.JAVA_UTIL_LIST)
            .put("extensionArtifacts", JavaClassNames.JAVA_UTIL_LIST)
            .put("extensionArtifactMap", JavaClassNames.JAVA_UTIL_LIST)
            .put("parentArtifact", JavaClassNames.JAVA_UTIL_LIST)
            .put("repositories", JavaClassNames.JAVA_UTIL_LIST)
            .put("reportPlugins", JavaClassNames.JAVA_UTIL_LIST)
            .put("buildPlugins", JavaClassNames.JAVA_UTIL_LIST)
            .put("modules", JavaClassNames.JAVA_UTIL_LIST)
            .put("modelBuild", "org.apache.maven.model.Build")
            .put("remoteArtifactRepositories", JavaClassNames.JAVA_UTIL_LIST)
            .put("pluginArtifactRepositories", JavaClassNames.JAVA_UTIL_LIST)
            .put("distributionManagementArtifactRepository", "org.apache.maven.artifact.repository.ArtifactRepository")
            .put("pluginRepositories", JavaClassNames.JAVA_UTIL_LIST)
            .put("remoteProjectRepositories", JavaClassNames.JAVA_UTIL_LIST)
            .put("remotePluginRepositories", JavaClassNames.JAVA_UTIL_LIST)
            .put("activeProfiles", JavaClassNames.JAVA_UTIL_LIST)
            .put("injectedProfileIds", JavaClassNames.JAVA_UTIL_LIST)
            .put("attachedArtifacts", JavaClassNames.JAVA_UTIL_LIST)
            .put("executionProject", MAVEN_PROJECT_CLASS)
            .put("collectedProjects", JavaClassNames.JAVA_UTIL_LIST)
            .put("dependencyArtifacts", JavaClassNames.JAVA_UTIL_LIST)
            .put("managedVersionMap", JavaClassNames.JAVA_UTIL_LIST)
            .put("buildExtensions", JavaClassNames.JAVA_UTIL_LIST)
            .put("properties", JavaClassNames.JAVA_UTIL_LIST)
            .put("filters", JavaClassNames.JAVA_UTIL_LIST)
            .put("projectReferences", JavaClassNames.JAVA_UTIL_LIST)
            .put("executionRoot", "boolean")
            .put("defaultGoal", JavaClassNames.JAVA_UTIL_LIST)
            .put("releaseArtifactRepository", "org.apache.maven.artifact.repository.ArtifactRepository")
            .put("snapshotArtifactRepository", "org.apache.maven.artifact.repository.ArtifactRepository")
            .put("classRealm", "org.codehaus.plexus.classworlds.realm.ClassRealm")
            .put("extensionDependencyFilter", "org.sonatype.aether.graph.DependencyFilter")
            .put("projectBuildingRequest", "org.apache.maven.project.ProjectBuildingRequest")

            .build()
        );

        res.put(MAVEN_MODEL_CLASS, ImmutableMap.<String, String>builder()
            .put("modelVersion", JavaClassNames.JAVA_LANG_STRING)
            .put("parent", MAVEN_PROJECT_CLASS)
            .put("groupId", JavaClassNames.JAVA_LANG_STRING)
            .put("artifactId", JavaClassNames.JAVA_LANG_STRING)
            .put("version", JavaClassNames.JAVA_LANG_STRING)
            .put("packaging", JavaClassNames.JAVA_LANG_STRING)
            .put("name", JavaClassNames.JAVA_LANG_STRING)
            .put("description", JavaClassNames.JAVA_LANG_STRING)
            .put("url", JavaClassNames.JAVA_LANG_STRING)
            .put("inceptionYear", JavaClassNames.JAVA_LANG_STRING)
            .put("organization", "org.apache.maven.model.Organization")
            .put("licenses", JavaClassNames.JAVA_UTIL_LIST)
            .put("developers", JavaClassNames.JAVA_UTIL_LIST)
            .put("contributors", JavaClassNames.JAVA_UTIL_LIST)
            .put("mailingLists", JavaClassNames.JAVA_UTIL_LIST)
            .put("prerequisites", "org.apache.maven.model.Prerequisites")
            .put("scm", "org.apache.maven.model.Scm")
            .put("issueManagement", "org.apache.maven.model.IssueManagement")
            .put("ciManagement", "org.apache.maven.model.CiManagement")
            .put("build", "org.apache.maven.model.Build")
            .put("profiles", JavaClassNames.JAVA_UTIL_LIST)
            .put("modelEncoding", JavaClassNames.JAVA_LANG_STRING)
            .put("pomFile", "java.io.File")
            .put("projectDirectory", "java.io.File")
            .put("id", JavaClassNames.JAVA_LANG_STRING)

            .put("repositories", JavaClassNames.JAVA_UTIL_LIST)
            .put("dependencies", JavaClassNames.JAVA_UTIL_LIST)
            .put("modules", JavaClassNames.JAVA_UTIL_LIST)
            .put("pluginRepositories", JavaClassNames.JAVA_UTIL_LIST)
            .put("properties", JavaClassNames.JAVA_UTIL_LIST)
            .put("reports", JavaClassNames.JAVA_LANG_OBJECT)
            .put("reporting", "org.apache.maven.model.Reporting")
            .build()
        );

        res.put(JavaClassNames.JAVA_UTIL_LIST, ImmutableMap.<String, String>of("empty", "boolean"));

        res.put("org.apache.maven.model.Build", ImmutableMap.<String, String>builder()
            .put("extensions", JavaClassNames.JAVA_UTIL_LIST)
            .put("filters", JavaClassNames.JAVA_UTIL_LIST)
            .put("resources", JavaClassNames.JAVA_UTIL_LIST)
            .put("testResources", JavaClassNames.JAVA_UTIL_LIST)
            .build());

        res.put("java.io.File", ImmutableMap.<String, String>builder()
            .put("prefixLength", "long")
            .put("name", JavaClassNames.JAVA_LANG_STRING)
            .put("parent", JavaClassNames.JAVA_LANG_STRING)
            .put("parentFile", "java.io.File")
            .put("path", JavaClassNames.JAVA_LANG_STRING)
            .put("absolute", "boolean")
            .put("absolutePath", JavaClassNames.JAVA_LANG_STRING)
            .put("absoluteFile", "java.io.File")
            .put("canonicalPath", JavaClassNames.JAVA_LANG_STRING)
            .put("canonicalFile", "java.io.File")
            .put("directory", "boolean")
            .put("file", "boolean")
            .put("hidden", "boolean")
            .put("totalSpace", "long")
            .put("freeSpace", "long")
            .put("usableSpace", "long")
            .build());

        PROPERTIES_MAP = res;
    }

    public static boolean isPathValid(@Nonnull String className, @Nonnull String path) {
        Map<String, String> cMap = PROPERTIES_MAP.get(className);
        if (cMap == null) {
            return false;
        }

        int idx = 0;

        do {
            int i = path.indexOf('.', idx);
            if (i == -1) {
                return cMap.containsKey(path.substring(idx));
            }

            cMap = PROPERTIES_MAP.get(cMap.get(path.substring(idx, i)));
            if (cMap == null) {
                return false;
            }

            idx = i + 1;
        }
        while (true);
    }

    public static Map<String, String> getCompletionVariants(@Nonnull String className, @Nonnull String path) {
        Map<String, String> cMap = PROPERTIES_MAP.get(className);
        if (cMap == null) {
            return Collections.emptyMap();
        }

        int idx = 0;

        do {
            int i = path.indexOf('.', idx);
            if (i == -1) {
                return cMap;
            }

            cMap = PROPERTIES_MAP.get(cMap.get(path.substring(idx, i)));
            if (cMap == null) {
                return Collections.emptyMap();
            }

            idx = i + 1;
        }
        while (true);
    }
}
