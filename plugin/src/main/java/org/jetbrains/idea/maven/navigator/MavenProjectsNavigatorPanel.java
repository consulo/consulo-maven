/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import consulo.annotation.access.RequiredReadAction;
import consulo.application.HelpManager;
import consulo.dataContext.DataProvider;
import consulo.execution.action.Location;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.maven.rt.server.common.model.MavenArtifact;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.maven.rt.server.common.model.MavenProfileKind;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.awt.PopupHandler;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.SimpleToolWindowPanel;
import consulo.ui.ex.awt.dnd.FileCopyPasteUtil;
import consulo.ui.ex.awt.tree.SimpleTree;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.execution.MavenGoalLocation;
import org.jetbrains.idea.maven.navigator.structure.*;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;

public class MavenProjectsNavigatorPanel extends SimpleToolWindowPanel implements DataProvider {
    private final Project myProject;
    private final SimpleTree myTree;

    private final Comparator<String> myGoalOrderComparator = new Comparator<>() {
        private Map<String, Integer> standardGoalOrder;

        @Override
        public int compare(String o1, String o2) {
            return getStandardGoalOrder(o1) - getStandardGoalOrder(o2);
        }

        private int getStandardGoalOrder(String goal) {
            if (standardGoalOrder == null) {
                standardGoalOrder = new HashMap<>();
                int i = 0;
                for (String aGoal : MavenConstants.PHASES) {
                    standardGoalOrder.put(aGoal, i++);
                }
            }
            Integer order = standardGoalOrder.get(goal);
            return order != null ? order : standardGoalOrder.size();
        }
    };

    public MavenProjectsNavigatorPanel(Project project, SimpleTree tree) {
        super(true, true);
        myProject = project;
        myTree = tree;

        final ActionManager actionManager = ActionManager.getInstance();
        ActionToolbar actionToolbar = actionManager.createActionToolbar("Maven Navigator Toolbar",
            (DefaultActionGroup)actionManager.getAction("Maven.NavigatorActionsToolbar"),
            true
        );

        actionToolbar.setTargetComponent(tree);
        setToolbar(actionToolbar.getComponent());
        setContent(ScrollPaneFactory.createScrollPane(myTree));

        setTransferHandler(new MyTransferHandler(project));

        myTree.addMouseListener(new PopupHandler() {
            @Override
            public void invokePopup(final Component comp, final int x, final int y) {
                final String id = getMenuId(getSelectedNodes(MavenSimpleNode.class));
                if (id != null) {
                    final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(id);
                    if (actionGroup != null) {
                        actionManager.createActionPopupMenu("", actionGroup).getComponent().show(comp, x, y);
                    }
                }
            }

            @Nullable
            private String getMenuId(Collection<? extends MavenSimpleNode> nodes) {
                String id = null;
                for (MavenSimpleNode node : nodes) {
                    String menuId = node.getMenuId();
                    if (menuId == null) {
                        return null;
                    }
                    if (id == null) {
                        id = menuId;
                    }
                    else if (!id.equals(menuId)) {
                        return null;
                    }
                }
                return id;
            }
        });
    }

    @Override
    @Nullable
    public Object getData(@Nonnull Key<?> dataId) {
        if (HelpManager.HELP_ID == dataId) {
            return "reference.toolWindows.mavenProjects";
        }

        if (Project.KEY == dataId) {
            return myProject;
        }

        if (VirtualFile.KEY == dataId) {
            return extractVirtualFile();
        }
        if (VirtualFile.KEY_OF_ARRAY == dataId) {
            return extractVirtualFiles();
        }

        if (Location.DATA_KEY == dataId) {
            return extractLocation();
        }
        if (Navigatable.KEY_OF_ARRAY == dataId) {
            return extractNavigatables();
        }

        if (MavenDataKeys.MAVEN_GOALS == dataId) {
            return extractGoals(true);
        }
        if (MavenDataKeys.MAVEN_PROFILES == dataId) {
            return extractProfiles();
        }

        if (MavenDataKeys.MAVEN_DEPENDENCIES == dataId) {
            return extractDependencies();
        }
        if (MavenDataKeys.MAVEN_PROJECTS_TREE == dataId) {
            return myTree;
        }

        return super.getData(dataId);
    }

    private VirtualFile extractVirtualFile() {
        for (MavenSimpleNode each : getSelectedNodes(MavenSimpleNode.class)) {
            VirtualFile file = each.getVirtualFile();
            if (file != null && file.isValid()) {
                return file;
            }
        }

        final ProjectNode projectNode = getContextProjectNode();
        if (projectNode == null) {
            return null;
        }
        VirtualFile file = projectNode.getVirtualFile();
        if (file == null || !file.isValid()) {
            return null;
        }
        return file;
    }

    private Object extractVirtualFiles() {
        final List<VirtualFile> files = new ArrayList<>();
        for (MavenSimpleNode each : getSelectedNodes(MavenSimpleNode.class)) {
            VirtualFile file = each.getVirtualFile();
            if (file != null && file.isValid()) {
                files.add(file);
            }
        }
        return files.isEmpty() ? null : VirtualFileUtil.toVirtualFileArray(files);
    }

    private Object extractNavigatables() {
        final List<Navigatable> navigatables = new ArrayList<>();
        for (MavenSimpleNode each : getSelectedNodes(MavenSimpleNode.class)) {
            Navigatable navigatable = each.getNavigatable();
            if (navigatable != null) {
                navigatables.add(navigatable);
            }
        }
        return navigatables.isEmpty() ? null : navigatables.toArray(new Navigatable[navigatables.size()]);
    }

    @RequiredReadAction
    private Object extractLocation() {
        VirtualFile file = extractVirtualFile();
        if (file == null) {
            return null;
        }

        List<String> goals = extractGoals(false);
        if (goals == null) {
            return null;
        }

        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
        return psiFile == null ? null : new MavenGoalLocation(myProject, psiFile, goals);
    }

    private List<String> extractGoals(boolean qualifiedGoals) {
        final ProjectNode projectNode = getSelectedProjectNode();
        if (projectNode != null) {
            MavenProject project = projectNode.getMavenProject();
            String goal = project.getDefaultGoal();
            if (!StringUtil.isEmptyOrSpaces(goal)) {
                // Maven uses StringTokenizer to split defaultGoal. See DefaultLifecycleTaskSegmentCalculator#calculateTaskSegments()
                return ContainerUtil.newArrayList(StringUtil.tokenize(new StringTokenizer(goal)));
            }
        }
        else {
            final List<GoalNode> nodes = getSelectedNodes(GoalNode.class);
            if (MavenProjectsStructure.getCommonProjectNode(nodes) == null) {
                return null;
            }
            final List<String> goals = new ArrayList<>();
            for (GoalNode node : nodes) {
                goals.add(qualifiedGoals ? node.getGoal() : node.getName());
            }
            Collections.sort(goals, myGoalOrderComparator);
            return goals;
        }
        return null;
    }

    private Object extractProfiles() {
        final List<ProfileNode> nodes = getSelectedNodes(ProfileNode.class);
        final Map<String, MavenProfileKind> profiles = new HashMap<>();
        for (ProfileNode node : nodes) {
            profiles.put(node.getProfileName(), node.getState());
        }
        return profiles;
    }

    private Set<MavenArtifact> extractDependencies() {
        Set<MavenArtifact> result = new HashSet<>();

        List<ProjectNode> projectNodes = getSelectedProjectNodes();
        if (!projectNodes.isEmpty()) {
            for (ProjectNode each : projectNodes) {
                result.addAll(each.getMavenProject().getDependencies());
            }
            return result;
        }

        List<BaseDependenciesNode> nodes = getSelectedNodes(BaseDependenciesNode.class);
        for (BaseDependenciesNode each : nodes) {
            if (each instanceof DependenciesNode) {
                result.addAll(each.getMavenProject().getDependencies());
            }
            else {
                result.add(((DependencyNode)each).getArtifact());
            }
        }
        return result;
    }

    private <T extends MavenSimpleNode> List<T> getSelectedNodes(Class<T> aClass) {
        return MavenProjectsStructure.getSelectedNodes(myTree, aClass);
    }

    private List<ProjectNode> getSelectedProjectNodes() {
        return getSelectedNodes(ProjectNode.class);
    }

    @Nullable
    private ProjectNode getSelectedProjectNode() {
        final List<ProjectNode> projectNodes = getSelectedProjectNodes();
        return projectNodes.size() == 1 ? projectNodes.get(0) : null;
    }

    @Nullable
    private ProjectNode getContextProjectNode() {
        ProjectNode projectNode = getSelectedProjectNode();
        if (projectNode != null) {
            return projectNode;
        }
        return MavenProjectsStructure.getCommonProjectNode(getSelectedNodes(MavenSimpleNode.class));
    }

    private static class MyTransferHandler extends TransferHandler {

        private final Project myProject;

        private MyTransferHandler(Project project) {
            myProject = project;
        }

        @Override
        public boolean importData(final TransferSupport support) {
            if (canImport(support)) {
                List<VirtualFile> pomFiles = new ArrayList<>();

                final List<File> fileList = FileCopyPasteUtil.getFileList(support.getTransferable());
                if (fileList == null) {
                    return false;
                }

                MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);

                for (File file : fileList) {
                    VirtualFile virtualFile = VirtualFileUtil.findFileByIoFile(file, true);
                    if (file.isFile() && virtualFile != null && MavenActionUtil.isMavenProjectFile(virtualFile) && !manager.isManagedFile
                        (virtualFile)) {
                        pomFiles.add(virtualFile);
                    }
                }

                if (pomFiles.isEmpty()) {
                    return false;
                }

                manager.addManagedFilesOrUnignore(pomFiles);

                return true;
            }
            return false;
        }

        @Override
        public boolean canImport(final TransferSupport support) {
            return FileCopyPasteUtil.isFileListFlavorAvailable(support.getDataFlavors());
        }
    }
}
