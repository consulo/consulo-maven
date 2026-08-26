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
package org.jetbrains.idea.maven.navigator.actions;

import consulo.ui.Tree;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class MavenTreeAction extends MavenAction {
    @Override
    protected boolean isAvailable(AnActionEvent e) {
        return super.isAvailable(e) && MavenActionUtil.isMavenizedProject(e.getDataContext()) && getTree(e) != null;
    }

    @Nullable
    protected static Tree<?> getTree(AnActionEvent e) {
        Tree<?> tree = e.getData(MavenDataKeys.MAVEN_PROJECTS_TREE);
        return tree != null && tree.isExpandCollapseAllSupported() ? tree : null;
    }

    public static class CollapseAll extends MavenTreeAction {
        @RequiredUIAccess
        @Override
        public void actionPerformed(@Nonnull AnActionEvent e) {
            Tree<?> tree = getTree(e);
            if (tree == null) {
                return;
            }

            tree.collapseAll();
        }
    }

    public static class ExpandAll extends MavenTreeAction {
        @RequiredUIAccess
        @Override
        public void actionPerformed(@Nonnull AnActionEvent e) {
            Tree<?> tree = getTree(e);
            if (tree == null) {
                return;
            }

            tree.expandAll();
        }
    }
}
