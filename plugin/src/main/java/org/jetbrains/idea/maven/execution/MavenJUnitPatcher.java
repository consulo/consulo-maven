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
package org.jetbrains.idea.maven.execution;

import com.intellij.java.execution.JavaTestPatcher;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.module.Module;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl
public class MavenJUnitPatcher implements JavaTestPatcher {
    @Override
    public void patchJavaParameters(@Nullable Module module, @Nonnull OwnJavaParameters javaParameters) {
        if (module == null) {
            return;
        }

        MavenProject mavenProject = MavenProjectsManager.getInstance(module.getProject()).findProject(module);
        if (mavenProject == null) {
            return;
        }

        Element config = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-surefire-plugin");
        if (config == null) {
            return;
        }

        List<String> paths =
            MavenJDOMUtil.findChildrenValuesByPath(config, "additionalClasspathElements", "additionalClasspathElement");

        if (paths.size() > 0) {
            MavenDomProjectModel domModel = MavenDomUtil.getMavenDomProjectModel(module.getProject(), mavenProject.getFile());

            for (String path : paths) {
                if (domModel != null) {
                    path = MavenPropertyResolver.resolve(path, domModel);
                }

                javaParameters.getClassPath().add(path);
            }
        }

        Element systemPropertyVariables = config.getChild("systemPropertyVariables");
        if (systemPropertyVariables != null) {
            for (Element element : systemPropertyVariables.getChildren()) {
                String propertyName = element.getName();

                if (!javaParameters.getVMParametersList().hasProperty(propertyName)) {
                    javaParameters.getVMParametersList().addProperty(propertyName, element.getValue());
                }
            }
        }

        Element environmentVariables = config.getChild("environmentVariables");
        if (environmentVariables != null) {
            for (Element element : environmentVariables.getChildren()) {
                String variableName = element.getName();

                if (javaParameters.getEnv() == null || !javaParameters.getEnv().containsKey(variableName)) {
                    javaParameters.addEnv(variableName, element.getValue());
                }
            }
        }
    }
}
