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

package org.jetbrains.idea.maven.dom.annotator;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomDependencyManagement;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.dom.model.MavenDomPluginManagement;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProject;
import com.intellij.codeInsight.navigation.DomNavigationGutterIconBuilder;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import consulo.ui.image.Image;

public class MavenDomGutterAnnotator implements Annotator {

  private static void annotateDependencyUsages(@Nonnull MavenDomDependency dependency, AnnotationHolder holder) {
    final XmlTag tag = dependency.getXmlTag();
    if (tag == null) return;

    final Set<MavenDomDependency> children = MavenDomProjectProcessorUtils.searchDependencyUsages(dependency);
    if (children.size() > 0) {
      final DomNavigationGutterIconBuilder<MavenDomDependency> iconBuilder =
        DomNavigationGutterIconBuilder.create(AllIcons.General.OverridenMethod, DependencyConverter.INSTANCE);
      iconBuilder.
        setTargets(children).
        setPopupTitle(MavenDomBundle.message("navigate.parent.dependency.title")).
        setCellRenderer(MyListCellRenderer.INSTANCE).
        setTooltipText(MavenDomBundle.message("overriding.dependency.title")).
        install(holder, dependency.getXmlTag());
    }
  }

  private static void annotateManagedDependency(MavenDomDependency dependency, AnnotationHolder holder) {
    final XmlTag tag = dependency.getXmlTag();
    if (tag == null) return;

    final List<MavenDomDependency> children = getManagingDependencies(dependency);
    if (children.size() > 0) {

      final DomNavigationGutterIconBuilder<MavenDomDependency> iconBuilder =
        DomNavigationGutterIconBuilder.create(AllIcons.General.OverridingMethod, DependencyConverter.INSTANCE);
      iconBuilder.
        setTargets(children).
        setTooltipText(MavenDomBundle.message("overriden.dependency.title")).
        install(holder, tag);
    }
  }

  private static List<MavenDomDependency> getManagingDependencies(@Nonnull MavenDomDependency dependency) {
    Project project = dependency.getManager().getProject();
    MavenDomDependency parentDependency = MavenDomProjectProcessorUtils.searchManagingDependency(dependency, project);

    if (parentDependency != null) {
      return Collections.singletonList(parentDependency);
    }
    return Collections.emptyList();
  }

  public void annotate(@Nonnull PsiElement psiElement, @Nonnull AnnotationHolder holder) {
    if (psiElement instanceof XmlTag) {
      final DomElement element = DomManager.getDomManager(psiElement.getProject()).getDomElement((XmlTag)psiElement);
      if (element instanceof MavenDomDependency) {
        if (element.getParentOfType(MavenDomPlugin.class, true) != null) return;

        MavenDomDependency dependency = (MavenDomDependency)element;
        if (isDependencyManagementSection(dependency)) {
          annotateDependencyUsages(dependency, holder);
        }
        else {
          annotateManagedDependency(dependency, holder);
        }
      }
      else if (element instanceof MavenDomParent) {
        annotateMavenDomParent((MavenDomParent)element, holder);
      }
      else if (element instanceof MavenDomProjectModel) {
        annotateMavenDomProjectChildren((MavenDomProjectModel)element, holder);
      }
      else if (element instanceof MavenDomPlugin) {
        annotateMavenDomPlugin((MavenDomPlugin)element, holder);
      }
    }
  }

  private static void annotateMavenDomPlugin(@Nonnull MavenDomPlugin plugin, @Nonnull AnnotationHolder holder) {
    XmlTag xmlTag = plugin.getArtifactId().getXmlTag();
    if (xmlTag == null) return;

    DomElement plugins = plugin.getParent();
    if (plugins == null) return;

    DomElement parent = plugins.getParent();
    if (parent instanceof MavenDomPluginManagement) {
      annotateMavenDomPluginInManagement(plugin, holder);
      return;
    }

    MavenDomPlugin managingPlugin = MavenDomProjectProcessorUtils.searchManagingPlugin(plugin);

    if (managingPlugin != null) {
      DomNavigationGutterIconBuilder<MavenDomPlugin> iconBuilder =
        DomNavigationGutterIconBuilder.create(AllIcons.General.OverridingMethod, PluginConverter.INSTANCE);

      iconBuilder.
        setTargets(Collections.singletonList(managingPlugin)).
        setTooltipText(MavenDomBundle.message("overriden.plugin.title")).
        install(holder, xmlTag);
    }
  }

  private static void annotateMavenDomPluginInManagement(@Nonnull MavenDomPlugin plugin, @Nonnull AnnotationHolder holder) {
    XmlTag xmlTag = plugin.getArtifactId().getXmlTag();
    if (xmlTag == null) return;

    Collection<MavenDomPlugin> children = MavenDomProjectProcessorUtils.searchManagedPluginUsages(plugin);

    if (children.size() > 0) {
      DomNavigationGutterIconBuilder<MavenDomPlugin> iconBuilder =
        DomNavigationGutterIconBuilder.create(AllIcons.General.OverridenMethod, PluginConverter.INSTANCE);

      iconBuilder.
        setTargets(children).
        setPopupTitle(MavenDomBundle.message("navigate.parent.plugin.title")).
        setCellRenderer(MyListCellRenderer.INSTANCE).
        setTooltipText(MavenDomBundle.message("overriding.plugin.title")).
        install(holder, xmlTag);
    }
  }


  private static void annotateMavenDomParent(@Nonnull MavenDomParent mavenDomParent, @Nonnull AnnotationHolder holder) {
    MavenDomProjectModel parent = MavenDomProjectProcessorUtils.findParent(mavenDomParent, mavenDomParent.getManager().getProject());

    if (parent != null) {
      NavigationGutterIconBuilder.create(icons.MavenIcons.ParentProject, MavenProjectConverter.INSTANCE).
        setTargets(parent).
        setTooltipText(MavenDomBundle.message("parent.pom.title")).
        install(holder, mavenDomParent.getXmlElement());
    }
  }

  private static void annotateMavenDomProjectChildren(MavenDomProjectModel model, AnnotationHolder holder) {
    MavenProject mavenProject = MavenDomUtil.findProject(model);
    if (mavenProject != null) {
      Set<MavenDomProjectModel> children = MavenDomProjectProcessorUtils.getChildrenProjects(model);

      if (children.size() > 0) {
        NavigationGutterIconBuilder.create(icons.MavenIcons.ChildrenProjects, MavenProjectConverter.INSTANCE).
          setTargets(children).
          setCellRenderer(MyListCellRenderer.INSTANCE).
          setPopupTitle(MavenDomBundle.message("navigate.children.poms.title")).
          setTooltipText(MavenDomBundle.message("children.poms.title")).
          install(holder, model.getXmlElement());
      }
    }
  }

  private static boolean isDependencyManagementSection(@Nonnull MavenDomDependency dependency) {
    return dependency.getParentOfType(MavenDomDependencyManagement.class, false) != null;
  }

  private static class MyListCellRenderer extends PsiElementListCellRenderer<XmlTag> {
    public static final MyListCellRenderer INSTANCE = new MyListCellRenderer();

    @Override
    public String getElementText(XmlTag tag) {
      DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
      if (domElement != null) {
        MavenDomProjectModel model = domElement.getParentOfType(MavenDomProjectModel.class, false);
        if (model != null) {
          MavenProject mavenProject = MavenDomUtil.findProject(model);
          if (mavenProject != null) return mavenProject.getDisplayName();

          String name = model.getName().getStringValue();
          if (!StringUtil.isEmptyOrSpaces(name)) {
            return name;
          }
        }
      }

      return tag.getContainingFile().getName();
    }

    @Override
    protected String getContainerText(XmlTag element, String name) {
      return null;
    }

    @Override
    protected Image getIcon(PsiElement element) {
      return icons.MavenIcons.MavenProject;
    }

    @Override
    protected int getIconFlags() {
      return 0;
    }
  }

  private static class DependencyConverter implements NotNullFunction<MavenDomDependency, Collection<? extends PsiElement>> {
    public static final DependencyConverter INSTANCE = new DependencyConverter();

    @Nonnull
    public Collection<? extends PsiElement> fun(final MavenDomDependency pointer) {
      return ContainerUtil.createMaybeSingletonList(pointer.getXmlTag());
    }
  }

  private static class PluginConverter implements NotNullFunction<MavenDomPlugin, Collection<? extends PsiElement>> {
    public static final PluginConverter INSTANCE = new PluginConverter();

    @Nonnull
    public Collection<? extends PsiElement> fun(final MavenDomPlugin pointer) {
      return ContainerUtil.createMaybeSingletonList(pointer.getXmlTag());
    }
  }

  private static class MavenProjectConverter implements NotNullFunction<MavenDomProjectModel, Collection<? extends PsiElement>> {
    public static final MavenProjectConverter INSTANCE = new MavenProjectConverter();

    @Nonnull
    public Collection<? extends PsiElement> fun(final MavenDomProjectModel pointer) {
      return ContainerUtil.createMaybeSingletonList(pointer.getXmlTag());
    }
  }
}
