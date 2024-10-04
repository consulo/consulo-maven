/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.navigator;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.disposer.Disposable;
import consulo.execution.event.RunManagerListener;
import consulo.execution.event.RunManagerListenerEvent;
import consulo.ide.ServiceManager;
import consulo.maven.rt.server.common.server.NativeMavenProjectHolder;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.project.ui.wm.ToolWindowManagerListener;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.JBHtmlEditorKit;
import consulo.ui.ex.awt.tree.SimpleTree;
import consulo.ui.ex.awt.tree.TreeState;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentFactory;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdom.Element;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.Collections;
import java.util.List;

@Singleton
@State(name = "MavenProjectNavigator", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class MavenProjectsNavigator extends MavenSimpleProjectComponent implements PersistentStateComponent<MavenProjectsNavigatorState>, Disposable {
    public static final String TOOL_WINDOW_ID = "Maven";

    private MavenProjectsNavigatorState myState = new MavenProjectsNavigatorState();

    private MavenProjectsManager myProjectsManager;
    private MavenTasksManager myTasksManager;
    private MavenShortcutsManager myShortcutsManager;

    private SimpleTree myTree;
    private MavenProjectsStructure myStructure;

    @Nonnull
    public static MavenProjectsNavigator getInstance(Project project) {
        return ServiceManager.getService(project, MavenProjectsNavigator.class);
    }

    @Inject
    public MavenProjectsNavigator(
        Project project,
        MavenProjectsManager projectsManager,
        MavenTasksManager tasksManager,
        MavenShortcutsManager shortcutsManager
    ) {
        super(project);
        myProjectsManager = projectsManager;
        myTasksManager = tasksManager;
        myShortcutsManager = shortcutsManager;

        if (!project.isDefault()) {
            listenForProjectsChanges();
        }
    }

    @Override
    public MavenProjectsNavigatorState getState() {
        Application.get().assertIsDispatchThread();
        if (myStructure != null) {
            try {
                myState.treeState = new Element("root");
                TreeState.createOn(myTree).writeExternal(myState.treeState);
            }
            catch (WriteExternalException e) {
                MavenLog.LOG.warn(e);
            }
        }
        return myState;
    }

    @Override
    public void loadState(MavenProjectsNavigatorState state) {
        myState = state;
        scheduleStructureUpdate();
    }

    public boolean getGroupModules() {
        return myState.groupStructurally;
    }

    public void setGroupModules(boolean value) {
        if (myState.groupStructurally != value) {
            myState.groupStructurally = value;
            scheduleStructureUpdate();
        }
    }

    public boolean getShowIgnored() {
        return myState.showIgnored;
    }

    public void setShowIgnored(boolean value) {
        if (myState.showIgnored != value) {
            myState.showIgnored = value;
            scheduleStructureUpdate();
        }
    }

    public boolean getShowBasicPhasesOnly() {
        return myState.showBasicPhasesOnly;
    }

    public void setShowBasicPhasesOnly(boolean value) {
        if (myState.showBasicPhasesOnly != value) {
            myState.showBasicPhasesOnly = value;
            scheduleStructureUpdate();
        }
    }

    public boolean getAlwaysShowArtifactId() {
        return myState.alwaysShowArtifactId;
    }

    public void setAlwaysShowArtifactId(boolean value) {
        if (myState.alwaysShowArtifactId != value) {
            myState.alwaysShowArtifactId = value;
            scheduleStructureUpdate();
        }
    }

    public boolean getShowVersions() {
        return myState.showVersions;
    }

    public void setShowVersions(boolean value) {
        if (myState.showVersions != value) {
            myState.showVersions = value;
            scheduleStructureUpdate();
        }
    }

    @TestOnly
    public void initForTests() {
        initTree();
        initStructure();
    }


    @Override
    public void dispose() {
        myProjectsManager = null;
    }

    private void listenForProjectsChanges() {
        myProjectsManager.addProjectsTreeListener(new MyProjectsListener());

        myShortcutsManager.addListener(() -> scheduleStructureRequest(() -> myStructure.updateGoals()));

        myTasksManager.addListener(() -> scheduleStructureRequest(() -> myStructure.updateGoals()));

        MavenRunner.getInstance(myProject).getSettings().addListener(() -> scheduleStructureRequest(() -> myStructure.updateGoals()));

        myProject.getMessageBus().connect().subscribe(RunManagerListener.class, new RunManagerListener() {
            private void changed() {
                scheduleStructureRequest(() -> myStructure.updateRunConfigurations());
            }

            @Override
            public void runConfigurationAdded(@Nonnull RunManagerListenerEvent event) {
                changed();
            }

            @Override
            public void runConfigurationRemoved(@Nonnull RunManagerListenerEvent event) {
                changed();
            }

            @Override
            public void runConfigurationChanged(@Nonnull RunManagerListenerEvent event) {
                changed();
            }

            @Override
            public void beforeRunTasksChanged(RunManagerListenerEvent event) {
                scheduleStructureRequest(() -> myStructure.updateGoals());
            }
        });
    }

    public void initToolWindow(ToolWindow toolWindow) {
        initTree();
        JPanel panel = new MavenProjectsNavigatorPanel(myProject, myTree);

        AnAction removeAction = EmptyAction.wrap(ActionManager.getInstance().getAction("Maven.RemoveRunConfiguration"));
        removeAction.registerCustomShortcutSet(CommonShortcuts.getDelete(), myTree, myProject);
        AnAction editSource = EmptyAction.wrap(ActionManager.getInstance().getAction("Maven.EditRunConfiguration"));
        editSource.registerCustomShortcutSet(CommonShortcuts.getEditSource(), myTree, myProject);

        final ContentFactory contentFactory = ContentFactory.getInstance();
        final Content content = contentFactory.createContent(panel, "", false);
        ContentManager contentManager = toolWindow.getContentManager();
        contentManager.addContent(content);
        contentManager.setSelectedContent(content, false);

        final ToolWindowManagerListener listener = new ToolWindowManagerListener() {
            boolean wasVisible = false;

            @Override
            public void stateChanged(ToolWindowManager toolWindowManager) {
                if (toolWindow.isDisposed()) {
                    return;
                }
                boolean visible = toolWindow.isVisible();
                if (!visible || wasVisible) {
                    return;
                }
                scheduleStructureUpdate();
                wasVisible = true;
            }
        };
        myProject.getMessageBus().connect(this).subscribe(ToolWindowManagerListener.class, listener);

        ActionManager actionManager = ActionManager.getInstance();

        ActionGroup.Builder group = ActionGroup.newImmutableBuilder();
        group.add(actionManager.getAction("Maven.GroupProjects"));
        group.add(actionManager.getAction("Maven.ShowIgnored"));
        group.add(actionManager.getAction("Maven.ShowBasicPhasesOnly"));
        group.add(actionManager.getAction("Maven.AlwaysShowArtifactId"));
        group.add(actionManager.getAction("Maven.ShowVersions"));

        toolWindow.setAdditionalGearActions(group.build());
    }

    private void initTree() {
        myTree = new SimpleTree() {
            private final JEditorPane myLabel;

            {
                myLabel = new JEditorPane("text/html", "");
                myLabel.setOpaque(false);
                myLabel.setEditorKit(JBHtmlEditorKit.create());
                String text = ProjectBundle.message("maven.navigator.nothing.to.display",
                    MavenUtil.formatHtmlImage(PlatformIconGroup.generalAdd()),
                    MavenUtil.formatHtmlImage(PlatformIconGroup
                        .actionsRefresh())
                );
                myLabel.setText(text);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (myProjectsManager.hasProjects()) {
                    return;
                }

                myLabel.setFont(getFont());
                myLabel.setBackground(getBackground());
                myLabel.setForeground(getForeground());
                Rectangle bounds = getBounds();
                Dimension size = myLabel.getPreferredSize();
                myLabel.setBounds(0, 0, size.width, size.height);

                int x = (bounds.width - size.width) / 2;
                Graphics g2 = g.create(bounds.x + x, bounds.y + 20, bounds.width, bounds.height);
                try {
                    myLabel.paint(g2);
                }
                finally {
                    g2.dispose();
                }
            }
        };
        myTree.getEmptyText().clear();

        myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    }

    @TestOnly
    public MavenProjectsStructure getStructureForTests() {
        return myStructure;
    }

    public void selectInTree(final MavenProject project) {
        scheduleStructureRequest(() -> myStructure.select(project));
    }

    private void scheduleStructureRequest(final Runnable r) {
        if (isUnitTestMode()) {
            if (myStructure != null) {
                r.run();
            }
            return;
        }

        ToolWindow toolWindow = getToolWindow();
        if (toolWindow == null) {
            return;
        }
        MavenUtil.invokeLater(myProject, () ->
        {
            if (!toolWindow.isVisible()) {
                return;
            }

            boolean shouldCreate = myStructure == null;
            if (shouldCreate) {
                initStructure();
            }

            r.run();

            if (shouldCreate) {
                TreeState.createFrom(myState.treeState).applyTo(myTree);
            }
        });
    }

    @Nullable
    public ToolWindow getToolWindow() {
        if (myProject.isDisposedOrDisposeInProgress()) {
            return null;
        }
        return ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID);
    }

    private void initStructure() {
        myStructure = new MavenProjectsStructure(myProject, myProjectsManager, myTasksManager, myShortcutsManager, this, myTree);
    }

    private void scheduleStructureUpdate() {
        scheduleStructureRequest(() -> myStructure.update());
    }

    private class MyProjectsListener implements MavenProjectsManager.Listener, MavenProjectsTree.Listener {
        @Override
        public void activated() {
            scheduleStructureUpdate();
        }

        @Override
        public void projectsIgnoredStateChanged(final List<MavenProject> ignored, final List<MavenProject> unignored, boolean fromImport) {
            scheduleStructureRequest(() -> myStructure.updateIgnored(ContainerUtil.concat(ignored, unignored)));
        }

        @Override
        public void profilesChanged() {
            scheduleStructureRequest(() -> myStructure.updateProfiles());
        }

        @Override
        public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
            scheduleUpdateProjects(MavenUtil.collectFirsts(updated), deleted);
        }

        @Override
        public void projectResolved(
            Pair<MavenProject, MavenProjectChanges> projectWithChanges,
            NativeMavenProjectHolder nativeMavenProject
        ) {
            scheduleUpdateProjects(Collections.singletonList(projectWithChanges.first), Collections.emptyList());
        }

        @Override
        public void pluginsResolved(MavenProject project) {
            scheduleUpdateProjects(Collections.singletonList(project), Collections.emptyList());
        }

        private void scheduleUpdateProjects(final List<MavenProject> projects, final List<MavenProject> deleted) {
            scheduleStructureRequest(() -> myStructure.updateProjects(projects, deleted));
        }
    }
}
