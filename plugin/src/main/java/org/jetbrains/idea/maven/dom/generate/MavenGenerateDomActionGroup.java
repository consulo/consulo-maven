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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomRepository;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Function;

public class MavenGenerateDomActionGroup extends DefaultActionGroup
{
	public MavenGenerateDomActionGroup()
	{
		add(new GenerateDependencyAction());
		add(new GenerateManagedDependencyAction());

		addSeparator();
		add(createAction(MavenDomBundle.message("generate.dependency.template"), MavenDomDependency.class, "maven-dependency",
				mavenDomProjectModel -> mavenDomProjectModel.getDependencies()));
		add(createAction(MavenDomBundle.message("generate.plugin.template"), MavenDomPlugin.class, "maven-plugin",
				mavenDomProjectModel -> mavenDomProjectModel.getBuild().getPlugins()));

		add(createAction(MavenDomBundle.message("generate.repository.template"), MavenDomRepository.class, "maven-repository",
				mavenDomProjectModel -> mavenDomProjectModel.getRepositories()));

		addSeparator();
		add(new GenerateParentAction());
	}

	private static MavenGenerateTemplateAction createAction(String actionDescription,
															final Class<? extends DomElement> aClass,
															@NonNls @Nullable String mappingId,
															@Nonnull Function<MavenDomProjectModel, DomElement> parentFunction)
	{
		return new MavenGenerateTemplateAction(actionDescription, aClass, mappingId, parentFunction);
	}
}