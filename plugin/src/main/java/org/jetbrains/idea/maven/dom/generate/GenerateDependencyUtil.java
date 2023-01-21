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

import consulo.application.AllIcons;
import consulo.ide.impl.idea.ide.util.MemberChooser;
import consulo.language.editor.generation.ClassMember;
import consulo.language.editor.generation.MemberChooserObject;
import consulo.language.editor.generation.MemberChooserObjectBase;
import consulo.language.editor.generation.PsiElementMemberChooserObject;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import org.jetbrains.idea.maven.MavenIcons;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.localize.MavenDomLocalize;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Serega.Vasiliev
 */
public class GenerateDependencyUtil
{
	private GenerateDependencyUtil()
	{
	}

	@Nonnull
	public static List<MavenDomDependency> chooseDependencies(Collection<MavenDomDependency> candidates, final Project project)
	{
		List<MavenDomDependency> dependencies = new ArrayList<MavenDomDependency>();

		MavenDomProjectModelMember[] memberCandidates =
				ContainerUtil.map2Array(candidates, MavenDomProjectModelMember.class, dependency -> new MavenDomProjectModelMember(dependency));
		MemberChooser<MavenDomProjectModelMember> chooser =
				new MemberChooser<MavenDomProjectModelMember>(memberCandidates, true, true, project)
				{
					protected ShowContainersAction getShowContainersAction()
					{
						return new ShowContainersAction(MavenDomLocalize.chooserShowProjectFiles(), MavenIcons.MavenProject);
					}

					protected String getAllContainersNodeName()
					{
						return MavenDomBundle.message("all.dependencies");
					}
				};

		chooser.setTitle(MavenDomBundle.message("dependencies.chooser.title"));
		chooser.setCopyJavadocVisible(false);
		chooser.show();

		if(chooser.getExitCode() == MemberChooser.OK_EXIT_CODE)
		{
			final MavenDomProjectModelMember[] members = chooser.getSelectedElements(new MavenDomProjectModelMember[0]);
			if(members != null)
			{
				dependencies.addAll(ContainerUtil.mapNotNull(members, mavenDomProjectModelMember -> mavenDomProjectModelMember.getDependency()));
			}
		}

		return dependencies;
	}

	private static class MavenDomProjectModelMember extends MemberChooserObjectBase implements ClassMember
	{
		private final MavenDomDependency myDependency;

		public MavenDomProjectModelMember(final MavenDomDependency dependency)
		{
			super(dependency.toString(), AllIcons.Nodes.PpLib);
			myDependency = dependency;
		}

		@Override
		public String getText()
		{
			StringBuffer sb = new StringBuffer();

			append(sb, myDependency.getGroupId().getStringValue());
			append(sb, myDependency.getArtifactId().getStringValue());
			append(sb, myDependency.getVersion().getStringValue());

			return sb.toString();
		}

		private static void append(StringBuffer sb, String str)
		{
			if(!StringUtil.isEmptyOrSpaces(str))
			{
				if(sb.length() > 0)
				{
					sb.append(": ");
				}
				sb.append(str);
			}
		}

		public MemberChooserObject getParentNodeDelegate()
		{
			MavenDomDependency dependency = getDependency();

			return new MavenDomProjectModelFileMemberChooserObjectBase(dependency.getXmlTag().getContainingFile(),
					getProjectName(dependency));
		}

		@Nullable
		private static String getProjectName(@Nullable MavenDomDependency dependency)
		{
			if(dependency != null)
			{
				MavenDomProjectModel model = dependency.getParentOfType(MavenDomProjectModel.class, false);
				if(model != null)
				{
					String name = model.getName().getStringValue();
					return StringUtil.isEmptyOrSpaces(name) ? model.getArtifactId().getStringValue() : name;
				}
			}
			return null;
		}

		public MavenDomDependency getDependency()
		{
			return myDependency;
		}

		private static class MavenDomProjectModelFileMemberChooserObjectBase extends PsiElementMemberChooserObject
		{

			public MavenDomProjectModelFileMemberChooserObjectBase(@Nonnull final PsiFile psiFile, @Nullable String projectName)
			{
				super(psiFile, StringUtil.isEmptyOrSpaces(projectName) ? psiFile.getName() : projectName, MavenIcons.MavenProject);
			}
		}
	}
}
