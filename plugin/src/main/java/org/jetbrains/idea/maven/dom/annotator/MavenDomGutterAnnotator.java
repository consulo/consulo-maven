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

import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.ui.PsiElementListCellRenderer;
import consulo.language.editor.ui.navigation.NavigationGutterIconBuilder;
import consulo.language.psi.PsiElement;
import consulo.maven.icon.MavenIconGroup;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.xml.codeInsight.navigation.DomNavigationGutterIconBuilder;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomManager;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.project.MavenProject;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class MavenDomGutterAnnotator implements Annotator {
    private static void annotateDependencyUsages(@Nonnull MavenDomDependency dependency, AnnotationHolder holder) {
        final XmlTag tag = dependency.getXmlTag();
        if (tag == null) {
            return;
        }

        final Set<MavenDomDependency> children = MavenDomProjectProcessorUtils.searchDependencyUsages(dependency);
        if (children.size() > 0) {
            final DomNavigationGutterIconBuilder<MavenDomDependency> iconBuilder =
                DomNavigationGutterIconBuilder.create(PlatformIconGroup.gutterOverridenmethod(), DependencyConverter.INSTANCE);
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
        if (tag == null) {
            return;
        }

        final List<MavenDomDependency> children = getManagingDependencies(dependency);
        if (children.size() > 0) {

            final DomNavigationGutterIconBuilder<MavenDomDependency> iconBuilder =
                DomNavigationGutterIconBuilder.create(PlatformIconGroup.gutterOverridingmethod(), DependencyConverter.INSTANCE);
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

    @Override
    public void annotate(@Nonnull PsiElement psiElement, @Nonnull AnnotationHolder holder) {
        if (psiElement instanceof XmlTag) {
            final DomElement element = DomManager.getDomManager(psiElement.getProject()).getDomElement((XmlTag)psiElement);
            if (element instanceof MavenDomDependency) {
                if (element.getParentOfType(MavenDomPlugin.class, true) != null) {
                    return;
                }

                MavenDomDependency dependency = (MavenDomDependency)element;
                if (isDependencyManagementSection(dependency)) {
                    annotateDependencyUsages(dependency, holder);
                }
                else {
                    annotateManagedDependency(dependency, holder);
                }
            }
            else if (element instanceof MavenDomParent domParent) {
                annotateMavenDomParent(domParent, holder);
            }
            else if (element instanceof MavenDomProjectModel domProjectModel) {
                annotateMavenDomProjectChildren(domProjectModel, holder);
            }
            else if (element instanceof MavenDomPlugin domPlugin) {
                annotateMavenDomPlugin(domPlugin, holder);
            }
        }
    }

    private static void annotateMavenDomPlugin(@Nonnull MavenDomPlugin plugin, @Nonnull AnnotationHolder holder) {
        XmlTag xmlTag = plugin.getArtifactId().getXmlTag();
        if (xmlTag == null) {
            return;
        }

        DomElement plugins = plugin.getParent();
        if (plugins == null) {
            return;
        }

        DomElement parent = plugins.getParent();
        if (parent instanceof MavenDomPluginManagement) {
            annotateMavenDomPluginInManagement(plugin, holder);
            return;
        }

        MavenDomPlugin managingPlugin = MavenDomProjectProcessorUtils.searchManagingPlugin(plugin);

        if (managingPlugin != null) {
            DomNavigationGutterIconBuilder<MavenDomPlugin> iconBuilder =
                DomNavigationGutterIconBuilder.create(PlatformIconGroup.gutterOverridingmethod(), PluginConverter.INSTANCE);

            iconBuilder
                .setTargets(Collections.singletonList(managingPlugin))
                .setTooltipText(MavenDomBundle.message("overriden.plugin.title"))
                .install(holder, xmlTag);
        }
    }

    private static void annotateMavenDomPluginInManagement(@Nonnull MavenDomPlugin plugin, @Nonnull AnnotationHolder holder) {
        XmlTag xmlTag = plugin.getArtifactId().getXmlTag();
        if (xmlTag == null) {
            return;
        }

        Collection<MavenDomPlugin> children = MavenDomProjectProcessorUtils.searchManagedPluginUsages(plugin);

        if (children.size() > 0) {
            DomNavigationGutterIconBuilder<MavenDomPlugin> iconBuilder =
                DomNavigationGutterIconBuilder.create(PlatformIconGroup.gutterOverridenmethod(), PluginConverter.INSTANCE);

            iconBuilder
                .setTargets(children)
                .setPopupTitle(MavenDomBundle.message("navigate.parent.plugin.title"))
                .setCellRenderer(MyListCellRenderer.INSTANCE)
                .setTooltipText(MavenDomBundle.message("overriding.plugin.title"))
                .install(holder, xmlTag);
        }
    }


    private static void annotateMavenDomParent(@Nonnull MavenDomParent mavenDomParent, @Nonnull AnnotationHolder holder) {
        MavenDomProjectModel parent = MavenDomProjectProcessorUtils.findParent(mavenDomParent, mavenDomParent.getManager().getProject());

        if (parent != null) {
            NavigationGutterIconBuilder.create(MavenIconGroup.parentproject(), MavenProjectConverter.INSTANCE)
                .setTargets(parent)
                .setTooltipText(MavenDomBundle.message("parent.pom.title"))
                .install(holder, mavenDomParent.getXmlElement());
        }
    }

    private static void annotateMavenDomProjectChildren(MavenDomProjectModel model, AnnotationHolder holder) {
        MavenProject mavenProject = MavenDomUtil.findProject(model);
        if (mavenProject != null) {
            Set<MavenDomProjectModel> children = MavenDomProjectProcessorUtils.getChildrenProjects(model);

            if (children.size() > 0) {
                NavigationGutterIconBuilder.create(MavenIconGroup.childrenprojects(), MavenProjectConverter.INSTANCE)
                    .setTargets(children)
                    .setCellRenderer(MyListCellRenderer.INSTANCE)
                    .setPopupTitle(MavenDomBundle.message("navigate.children.poms.title"))
                    .setTooltipText(MavenDomBundle.message("children.poms.title"))
                    .install(holder, model.getXmlElement());
            }
        }
    }

    private static boolean isDependencyManagementSection(@Nonnull MavenDomDependency dependency) {
        return dependency.getParentOfType(MavenDomDependencyManagement.class, false) != null;
    }

    private static class MyListCellRenderer extends PsiElementListCellRenderer<XmlTag> {
        public static final MyListCellRenderer INSTANCE = new MyListCellRenderer();

        @Override
        @RequiredUIAccess
        public String getElementText(XmlTag tag) {
            DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
            if (domElement != null) {
                MavenDomProjectModel model = domElement.getParentOfType(MavenDomProjectModel.class, false);
                if (model != null) {
                    MavenProject mavenProject = MavenDomUtil.findProject(model);
                    if (mavenProject != null) {
                        return mavenProject.getDisplayName();
                    }

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
            return MavenIconGroup.mavenlogo();
        }

        @Override
        protected int getIconFlags() {
            return 0;
        }
    }

    private static class DependencyConverter implements Function<MavenDomDependency, Collection<? extends PsiElement>> {
        public static final DependencyConverter INSTANCE = new DependencyConverter();

        @Override
        @Nonnull
        public Collection<? extends PsiElement> apply(final MavenDomDependency pointer) {
            return ContainerUtil.createMaybeSingletonList(pointer.getXmlTag());
        }
    }

    private static class PluginConverter implements Function<MavenDomPlugin, Collection<? extends PsiElement>> {
        public static final PluginConverter INSTANCE = new PluginConverter();

        @Override
        @Nonnull
        public Collection<? extends PsiElement> apply(final MavenDomPlugin pointer) {
            return ContainerUtil.createMaybeSingletonList(pointer.getXmlTag());
        }
    }

    private static class MavenProjectConverter implements Function<MavenDomProjectModel, Collection<? extends PsiElement>> {
        public static final MavenProjectConverter INSTANCE = new MavenProjectConverter();

        @Override
        @Nonnull
        public Collection<? extends PsiElement> apply(final MavenDomProjectModel pointer) {
            return ContainerUtil.createMaybeSingletonList(pointer.getXmlTag());
        }
    }
}
