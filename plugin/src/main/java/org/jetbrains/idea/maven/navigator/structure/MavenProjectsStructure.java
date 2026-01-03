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
package org.jetbrains.idea.maven.navigator.structure;

import consulo.disposer.Disposer;
import consulo.maven.rt.server.common.model.*;
import consulo.project.Project;
import consulo.ui.ex.awt.tree.*;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.*;

import jakarta.annotation.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;

public class MavenProjectsStructure extends SimpleTreeStructure {
    public static final Collection<String> BASIC_PHASES = MavenConstants.BASIC_PHASES;
    public static final Collection<String> PHASES = MavenConstants.PHASES;

    public static final Comparator<MavenSimpleNode> NODE_COMPARATOR = (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true);

    private final Project myProject;
    private final MavenProjectsManager myProjectsManager;
    private final MavenTasksManager myTasksManager;
    private final MavenShortcutsManager myShortcutsManager;
    private final MavenProjectsNavigator myProjectsNavigator;

    private final SimpleTreeBuilder myTreeBuilder;
    private final RootNode myRoot = new RootNode(this);

    private final Map<MavenProject, ProjectNode> myProjectToNodeMapping = new HashMap<>();

    public MavenProjectsStructure(
        Project project,
        MavenProjectsManager projectsManager,
        MavenTasksManager tasksManager,
        MavenShortcutsManager shortcutsManager,
        MavenProjectsNavigator projectsNavigator,
        SimpleTree tree
    ) {
        myProject = project;
        myProjectsManager = projectsManager;
        myTasksManager = tasksManager;
        myShortcutsManager = shortcutsManager;
        myProjectsNavigator = projectsNavigator;

        configureTree(tree);

        myTreeBuilder = new SimpleTreeBuilder(tree, (DefaultTreeModel)tree.getModel(), this, null);
        Disposer.register(myProject, myTreeBuilder);

        myTreeBuilder.initRoot();
        myTreeBuilder.expand(myRoot, null);
    }

    public MavenProjectsNavigator getProjectsNavigator() {
        return myProjectsNavigator;
    }

    public MavenTasksManager getTasksManager() {
        return myTasksManager;
    }

    public MavenProjectsManager getProjectsManager() {
        return myProjectsManager;
    }

    public MavenShortcutsManager getShortcutsManager() {
        return myShortcutsManager;
    }

    public Project getProject() {
        return myProject;
    }

    private void configureTree(final SimpleTree tree) {
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);

        MavenUIUtil.installCheckboxRenderer(tree, new MavenUIUtil.CheckboxHandler() {
            @Override
            public void toggle(TreePath treePath, InputEvent e) {
                SimpleNode node = tree.getNodeFor(treePath);
                if (node != null) {
                    node.handleDoubleClickOrEnter(tree, e);
                }
            }

            @Override
            public boolean isVisible(Object userObject) {
                return userObject instanceof ProfileNode;
            }

            @Override
            public MavenUIUtil.CheckBoxState getState(Object userObject) {
                MavenProfileKind state = ((ProfileNode)userObject).getState();
                switch (state) {
                    case NONE:
                        return MavenUIUtil.CheckBoxState.UNCHECKED;
                    case EXPLICIT:
                        return MavenUIUtil.CheckBoxState.CHECKED;
                    case IMPLICIT:
                        return MavenUIUtil.CheckBoxState.PARTIAL;
                }
                MavenLog.LOG.error("unknown profile state: " + state);
                return MavenUIUtil.CheckBoxState.UNCHECKED;
            }
        });
    }

    @Nonnull
    @Override
    public RootNode getRootElement() {
        return myRoot;
    }

    public void update() {
        List<MavenProject> projects = myProjectsManager.getProjects();
        Set<MavenProject> deleted = new HashSet<>(myProjectToNodeMapping.keySet());
        deleted.removeAll(projects);
        updateProjects(projects, deleted);
    }

    public void updateFrom(@Nullable SimpleNode node) {
        if (node != null) {
            myTreeBuilder.addSubtreeToUpdateByElement(node);
        }
    }

    public void updateUpTo(SimpleNode node) {
        SimpleNode each = node;
        while (each != null) {
            updateFrom(each);
            each = each.getParent();
        }
    }

    public void updateProjects(List<MavenProject> updated, Collection<MavenProject> deleted) {
        for (MavenProject each : updated) {
            ProjectNode node = findNodeFor(each);
            if (node == null) {
                node = new ProjectNode(this, each);
                myProjectToNodeMapping.put(each, node);
            }
            doUpdateProject(node);
        }

        for (MavenProject each : deleted) {
            ProjectNode node = myProjectToNodeMapping.remove(each);
            if (node != null) {
                ProjectsGroupNode parent = node.getGroup();
                parent.remove(node);
            }
        }

        myRoot.updateProfiles();
    }

    private void doUpdateProject(ProjectNode node) {
        MavenProject project = node.getMavenProject();

        ProjectsGroupNode newParentNode = myRoot;

        if (myProjectsNavigator.getGroupModules()) {
            MavenProject aggregator = myProjectsManager.findAggregator(project);
            if (aggregator != null) {
                ProjectNode aggregatorNode = findNodeFor(aggregator);
                if (aggregatorNode != null && aggregatorNode.isVisible()) {
                    newParentNode = aggregatorNode.getModulesNode();
                }
            }
        }

        node.updateProject();
        reconnectNode(node, newParentNode);

        ProjectsGroupNode newModulesParentNode = myProjectsNavigator.getGroupModules() && node.isVisible() ? node.getModulesNode() : myRoot;
        for (MavenProject each : myProjectsManager.getModules(project)) {
            ProjectNode moduleNode = findNodeFor(each);
            if (moduleNode != null && !moduleNode.getParent().equals(newModulesParentNode)) {
                reconnectNode(moduleNode, newModulesParentNode);
            }
        }
    }

    private void reconnectNode(ProjectNode node, ProjectsGroupNode newParentNode) {
        ProjectsGroupNode oldParentNode = node.getGroup();
        if (oldParentNode == null || !oldParentNode.equals(newParentNode)) {
            if (oldParentNode != null) {
                oldParentNode.remove(node);
            }
            newParentNode.add(node);
        }
        else {
            newParentNode.sortProjects();
        }
    }

    public void updateProfiles() {
        myRoot.updateProfiles();
    }

    public void updateIgnored(List<MavenProject> projects) {
        for (MavenProject each : projects) {
            ProjectNode node = findNodeFor(each);
            if (node == null) {
                continue;
            }
            node.updateIgnored();
        }
    }

    public void accept(Predicate<SimpleNode> visitor) {
        ((SimpleTree)myTreeBuilder.getTree()).accept(myTreeBuilder, visitor);
    }

    public void updateGoals() {
        for (ProjectNode each : myProjectToNodeMapping.values()) {
            each.updateGoals();
        }
    }

    public void updateRunConfigurations() {
        for (ProjectNode each : myProjectToNodeMapping.values()) {
            each.updateRunConfigurations();
        }
    }

    public void select(MavenProject project) {
        ProjectNode node = findNodeFor(project);
        if (node != null) {
            select(node);
        }
    }

    public void select(SimpleNode node) {
        myTreeBuilder.select(node, null);
    }

    private ProjectNode findNodeFor(MavenProject project) {
        return myProjectToNodeMapping.get(project);
    }

    public boolean isShown(Class aClass) {
        Class<? extends MavenSimpleNode>[] classes = getVisibleNodesClasses();
        if (classes == null) {
            return true;
        }

        for (Class<? extends MavenSimpleNode> c : classes) {
            if (c == aClass) {
                return true;
            }
        }

        return false;
    }

    enum DisplayKind {
        ALWAYS,
        NEVER,
        NORMAL
    }

    protected Class<? extends MavenSimpleNode>[] getVisibleNodesClasses() {
        return null;
    }

    protected boolean showDescriptions() {
        return true;
    }

    protected boolean showOnlyBasicPhases() {
        return myProjectsNavigator.getShowBasicPhasesOnly();
    }

    public static <T extends MavenSimpleNode> List<T> getSelectedNodes(SimpleTree tree, Class<T> nodeClass) {
        final List<T> filtered = new ArrayList<>();
        for (SimpleNode node : getSelectedNodes(tree)) {
            if ((nodeClass != null) && (!nodeClass.isInstance(node))) {
                filtered.clear();
                break;
            }
            //noinspection unchecked
            filtered.add((T)node);
        }
        return filtered;
    }

    private static List<SimpleNode> getSelectedNodes(SimpleTree tree) {
        List<SimpleNode> nodes = new ArrayList<>();
        TreePath[] treePaths = tree.getSelectionPaths();
        if (treePaths != null) {
            for (TreePath treePath : treePaths) {
                nodes.add(tree.getNodeFor(treePath));
            }
        }
        return nodes;
    }

    @Nullable
    public static ProjectNode getCommonProjectNode(Collection<? extends MavenSimpleNode> nodes) {
        ProjectNode parent = null;
        for (MavenSimpleNode node : nodes) {
            ProjectNode nextParent = node.findParent(ProjectNode.class);
            if (parent == null) {
                parent = nextParent;
            }
            else if (parent != nextParent) {
                return null;
            }
        }
        return parent;
    }

    public enum ErrorLevel {
        NONE,
        ERROR
    }
}