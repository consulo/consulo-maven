/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import consulo.application.Application;
import consulo.ide.impl.ui.impl.PopupChooserBuilder;
import consulo.maven.icon.MavenIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.tree.NodeRenderer;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenProjectNamer;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Sergey Evdokimov
 */
public class MavenSelectProjectPopup {
    public static void attachToWorkingDirectoryField(
        @Nonnull final MavenProjectsManager projectsManager,
        final JTextField workingDirectoryField,
        final JButton showModulesButton,
        @Nullable final JComponent focusAfterSelection
    ) {
        attachToButton(
            projectsManager,
            showModulesButton,
            project -> {
                workingDirectoryField.setText(project.getDirectory());

                if (focusAfterSelection != null) {
                    Application.get().invokeLater(() -> {
                        if (workingDirectoryField.hasFocus()) {
                            focusAfterSelection.requestFocus();
                        }
                    });
                }
            }
        );

        workingDirectoryField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    e.consume();
                    showModulesButton.doClick();
                }
            }
        });
    }

    public static void attachToButton(
        @Nonnull final MavenProjectsManager projectsManager,
        @Nonnull final JButton button,
        @Nonnull final Consumer<MavenProject> callback
    ) {
        button.addActionListener(e -> buildPopup(projectsManager, callback).showUnderneathOf(button));
    }

    @Nonnull
    public static JBPopup buildPopup(MavenProjectsManager projectsManager, @Nonnull final Consumer<MavenProject> callback) {
        List<MavenProject> projectList = projectsManager.getProjects();
        if (projectList.isEmpty()) {
            return JBPopupFactory.getInstance().createMessage("Maven projects not found");
        }

        DefaultMutableTreeNode root = buildTree(projectsManager, projectList);

        final Map<MavenProject, String> projectsNameMap = MavenProjectNamer.generateNameMap(projectList);

        final Tree projectTree = new Tree(root);
        projectTree.setRootVisible(false);
        projectTree.setCellRenderer(new NodeRenderer() {
            @RequiredUIAccess
            @Override
            public void customizeCellRenderer(
                @Nonnull JTree tree,
                Object value,
                boolean selected,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus
            ) {
                if (value instanceof DefaultMutableTreeNode) {
                    MavenProject mavenProject = (MavenProject)((DefaultMutableTreeNode)value).getUserObject();
                    value = projectsNameMap.get(mavenProject);
                    setIcon(MavenIconGroup.mavenlogo());
                }

                super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
            }
        });

        new TreeSpeedSearch(
            projectTree,
            o -> {
                Object lastPathComponent = o.getLastPathComponent();
                if (!(lastPathComponent instanceof DefaultMutableTreeNode)) {
                    return null;
                }

                Object userObject = ((DefaultMutableTreeNode)lastPathComponent).getUserObject();

                //noinspection SuspiciousMethodCalls
                return projectsNameMap.get(userObject);
            }
        );

        final SimpleReference<JBPopup> popupRef = SimpleReference.create();

        Runnable clickCallBack = () -> {
            TreePath path = projectTree.getSelectionPath();
            if (path == null) {
                return;
            }

            Object lastPathComponent = path.getLastPathComponent();
            if (!(lastPathComponent instanceof DefaultMutableTreeNode)) {
                return;
            }

            Object object = ((DefaultMutableTreeNode)lastPathComponent).getUserObject();
            if (object == null) {
                return; // may be it's the root
            }

            callback.accept((MavenProject)object);

            popupRef.get().closeOk(null);
        };

        JBPopup popup = new PopupChooserBuilder(projectTree)
            .setTitle("Select maven project")
            .setResizable(true)
            .setItemChoosenCallback(clickCallBack).setAutoselectOnMouseMove(true)
            .setCloseOnEnter(false)
            .createPopup();

        popupRef.set(popup);

        return popup;
    }

    private static DefaultMutableTreeNode buildTree(MavenProjectsManager projectsManager, List<MavenProject> projectList) {
        MavenProject[] projects = projectList.toArray(new MavenProject[projectList.size()]);
        Arrays.sort(projects, new MavenProjectNamer.MavenProjectComparator());

        Map<MavenProject, DefaultMutableTreeNode> projectsToNode = new HashMap<>();
        for (MavenProject mavenProject : projects) {
            projectsToNode.put(mavenProject, new DefaultMutableTreeNode(mavenProject));
        }

        DefaultMutableTreeNode root = new DefaultMutableTreeNode();

        for (MavenProject mavenProject : projects) {
            DefaultMutableTreeNode parent;

            MavenProject aggregator = projectsManager.findAggregator(mavenProject);
            if (aggregator != null) {
                parent = projectsToNode.get(aggregator);
            }
            else {
                parent = root;
            }

            parent.add(projectsToNode.get(mavenProject));
        }

        return root;
    }
}
