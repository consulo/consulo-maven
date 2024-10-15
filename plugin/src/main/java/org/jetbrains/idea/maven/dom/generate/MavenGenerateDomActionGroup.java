/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.generate;

import consulo.ui.ex.action.DefaultActionGroup;
import consulo.xml.util.xml.DomElement;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.localize.MavenDomLocalize;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.function.Function;

public class MavenGenerateDomActionGroup extends DefaultActionGroup {
    public MavenGenerateDomActionGroup() {
        addAction(new GenerateDependencyAction());
        addAction(new GenerateManagedDependencyAction());

        addSeparator();
        addAction(createAction(
            MavenDomLocalize.generateDependencyTemplate().get(),
            MavenDomDependency.class,
            "maven-dependency",
            MavenDomProjectModelBase::getDependencies
        ));
        addAction(createAction(
            MavenDomLocalize.generatePluginTemplate().get(),
            MavenDomPlugin.class,
            "maven-plugin",
            mavenDomProjectModel -> mavenDomProjectModel.getBuild().getPlugins()
        ));

        addAction(createAction(
            MavenDomLocalize.generateRepositoryTemplate().get(),
            MavenDomRepository.class,
            "maven-repository",
            MavenDomProjectModelBase::getRepositories
        ));

        addSeparator();
        addAction(new GenerateParentAction());
    }

    private static MavenGenerateTemplateAction createAction(
        String actionDescription,
        final Class<? extends DomElement> aClass,
        @Nullable String mappingId,
        @Nonnull Function<MavenDomProjectModel, DomElement> parentFunction
    ) {
        return new MavenGenerateTemplateAction(actionDescription, aClass, mappingId, parentFunction);
    }
}