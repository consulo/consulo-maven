// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import consulo.maven.rt.server.common.model.MavenPlugin;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public final class BuildHelperMavenPluginUtil {
    public static @Nullable MavenPlugin findPlugin(MavenProject mavenProject) {
        return mavenProject.findPlugin("org.codehaus.mojo", "build-helper-maven-plugin");
    }

    public static void addBuilderHelperPaths(MavenProject mavenProject, String goal, Consumer<String> addFolder) {
        MavenPlugin plugin = findPlugin(mavenProject);
        if (plugin != null) {
            addBuilderHelperPaths(plugin, goal, addFolder);
        }
    }

    public static void addBuilderHelperPaths(MavenPlugin plugin, String goal, Consumer<String> addFolder) {
        for (MavenPlugin.Execution execution : plugin.getExecutions()) {
            if (execution.getGoals().contains(goal)) {
                Element configurationElement = execution.getConfigurationElement();
                if (configurationElement != null) {
                    Element sourcesElement = configurationElement.getChild("sources");
                    if (sourcesElement != null) {
                        for (Element element : sourcesElement.getChildren()) {
                            addFolder.accept(element.getTextTrim());
                        }
                    }
                }
            }
        }
    }

    public static void addBuilderHelperResourcesPaths(MavenProject mavenProject, String goal, Consumer<String> addFolder) {
        MavenPlugin plugin = findPlugin(mavenProject);
        if (plugin != null) {
            addBuilderHelperResourcesPaths(plugin, goal, addFolder);
        }
    }

    public static void addBuilderHelperResourcesPaths(MavenPlugin plugin, String goal, Consumer<String> addFolder) {
        for (MavenPlugin.Execution execution : plugin.getExecutions()) {
            if (execution.getGoals().contains(goal)) {
                Element configurationElement = execution.getConfigurationElement();
                if (configurationElement != null) {
                    Element sourcesElement = configurationElement.getChild("resources");
                    if (sourcesElement != null) {
                        for (Element element : sourcesElement.getChildren()) {
                            Element directory = element.getChild("directory");
                            if (directory != null) {
                                addFolder.accept(directory.getTextTrim());
                            }
                        }
                    }
                }
            }
        }
    }
}
