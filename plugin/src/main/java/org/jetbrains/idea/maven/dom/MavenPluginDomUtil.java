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
package org.jetbrains.idea.maven.dom;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiFile;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.maven.rt.server.common.model.MavenPlugin;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.xml.psi.xml.XmlElement;
import consulo.xml.util.xml.DomElement;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.dom.plugin.MavenDomPluginModel;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;

import jakarta.annotation.Nonnull;

import java.io.File;

public class MavenPluginDomUtil {
    @Nullable
    public static MavenProject findMavenProject(@Nonnull DomElement domElement) {
        XmlElement xmlElement = domElement.getXmlElement();
        if (xmlElement == null) {
            return null;
        }
        PsiFile psiFile = xmlElement.getContainingFile();
        if (psiFile == null) {
            return null;
        }
        VirtualFile file = psiFile.getVirtualFile();
        if (file == null) {
            return null;
        }
        return MavenProjectsManager.getInstance(psiFile.getProject()).findProject(file);
    }

    @Nullable
    @RequiredReadAction
    public static MavenDomPluginModel getMavenPluginModel(DomElement element) {
        Project project = element.getManager().getProject();

        MavenDomPlugin pluginElement = element.getParentOfType(MavenDomPlugin.class, false);
        if (pluginElement == null) {
            return null;
        }

        String groupId = pluginElement.getGroupId().getStringValue();
        String artifactId = pluginElement.getArtifactId().getStringValue();
        String version = pluginElement.getVersion().getStringValue();
        if (StringUtil.isEmpty(version)) {
            MavenProject mavenProject = findMavenProject(element);
            if (mavenProject != null) {
                for (MavenPlugin plugin : mavenProject.getPlugins()) {
                    if (MavenArtifactUtil.isPluginIdEquals(groupId, artifactId, plugin.getGroupId(), plugin.getArtifactId())) {
                        MavenId pluginMavenId = plugin.getMavenId();
                        version = pluginMavenId.getVersion();
                        break;
                    }
                }
            }
        }
        return getMavenPluginModel(project, groupId, artifactId, version);
    }

    @Nullable
    @RequiredReadAction
    public static MavenDomPluginModel getMavenPluginModel(Project project, String groupId, String artifactId, String version) {
        VirtualFile pluginXmlFile = getPluginXmlFile(project, groupId, artifactId, version);
        if (pluginXmlFile == null) {
            return null;
        }

        return MavenDomUtil.getMavenDomModel(project, pluginXmlFile, MavenDomPluginModel.class);
    }

    public static boolean isPlugin(@Nonnull MavenDomConfiguration configuration, @Nullable String groupId, @Nonnull String artifactId) {
        MavenDomPlugin domPlugin = configuration.getParentOfType(MavenDomPlugin.class, true);
        return domPlugin != null && isPlugin(domPlugin, groupId, artifactId);
    }

    public static boolean isPlugin(@Nonnull MavenDomPlugin plugin, @Nullable String groupId, @Nonnull String artifactId) {
        if (!artifactId.equals(plugin.getArtifactId().getStringValue())) {
            return false;
        }

        String pluginGroupId = plugin.getGroupId().getStringValue();

        if (groupId == null) {
            return pluginGroupId == null
                || pluginGroupId.equals("org.apache.maven.plugins")
                || pluginGroupId.equals("org.codehaus.mojo");
        }

        if (pluginGroupId == null && (groupId.equals("org.apache.maven.plugins") || groupId.equals("org.codehaus.mojo"))) {
            return true;
        }

        return groupId.equals(pluginGroupId);
    }

    @Nullable
    private static VirtualFile getPluginXmlFile(Project project, String groupId, String artifactId, String version) {
        File file = MavenArtifactUtil.getArtifactFile(
            MavenProjectsManager.getInstance(project).getLocalRepository(),
            groupId,
            artifactId,
            version,
            "jar"
        );
        VirtualFile pluginFile = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (pluginFile == null) {
            return null;
        }

        VirtualFile pluginJarRoot = ArchiveVfsUtil.getJarRootForLocalFile(pluginFile);
        if (pluginJarRoot == null) {
            return null;
        }
        return pluginJarRoot.findFileByRelativePath(MavenArtifactUtil.MAVEN_PLUGIN_DESCRIPTOR);
    }
}
