/*
 * Copyright 2013-2026 consulo.io
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

import consulo.ui.TreeNode;
import consulo.ui.ex.tree.SimpleTreeModel;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class MavenTreeStructureModel extends SimpleTreeModel<MavenSimpleNode> {
    private final Map<MavenSimpleNode, TreeNode<MavenSimpleNode>> myHandles = new ConcurrentHashMap<>();

    public MavenTreeStructureModel(MavenProjectsStructure structure) {
        super(structure::getRootElement);
    }

    @Override
    public void buildChildren(Function<MavenSimpleNode, TreeNode<MavenSimpleNode>> nodeFactory, @Nullable MavenSimpleNode parentValue) {
        super.buildChildren(node -> {
            TreeNode<MavenSimpleNode> handle = nodeFactory.apply(node);
            myHandles.put(node, handle);
            return handle;
        }, parentValue);
    }

    @Nullable
    public TreeNode<MavenSimpleNode> getHandle(MavenSimpleNode node) {
        return myHandles.get(node);
    }
}
