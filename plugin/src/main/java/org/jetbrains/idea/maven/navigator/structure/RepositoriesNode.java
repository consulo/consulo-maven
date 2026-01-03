// Copyright 2000-2023 JetBrains s.r.o. and contributors.
// Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.idea.maven.navigator.structure;

import consulo.maven.icon.MavenIconGroup;
import consulo.project.Project;
import consulo.util.lang.Pair;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.project.ProjectBundle;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

class RepositoriesNode extends GroupNode {

    private final List<RepositoryNode> myRepositoryNodes = new CopyOnWriteArrayList<>();

    RepositoriesNode(MavenProjectsStructure structure, ProjectNode parent) {
        super(structure, parent);
        getTemplatePresentation().setIcon(MavenIconGroup.profilesclosed());
    }

    @Override
    public String getName() {
        return ProjectBundle.message("view.node.repositories");
    }

    @Override
    protected List<RepositoryNode> doGetChildren() {
        return myRepositoryNodes;
    }

    public void updateRepositories(Project project) {
        Path local = myMavenProjectsStructure.getProjectsManager().getLocalRepository().toPath();

        MavenProjectIndicesManager indicesManager = MavenProjectIndicesManager.getInstance(project);

        Set<Pair<String, String>> remotes = indicesManager.collectRemoteRepositoriesIdsAndUrls();
        // TODO var remotes = MavenIndexUtils.getRemoteRepositoriesNoResolve(project);

        myRepositoryNodes.clear();

        myRepositoryNodes.add(
            new RepositoryNode(
                myMavenProjectsStructure,
                this,
                "local",
                local.toAbsolutePath().toString(),
                true
            )
        );
        
        myRepositoryNodes.addAll(
            remotes.stream()
                .map(it -> new RepositoryNode(
                    myMavenProjectsStructure,
                    this,
                    it.getKey(),
                    it.getSecond(),
                    false
                ))
                .collect(Collectors.toList())
        );

        childrenChanged();
    }
// FIXME we don't have index system from IDEA
//    public void updateStatus(MavenIndexUpdateState state) {
//        List<RepositoryNode> nodesToUpdate = myRepositoryNodes.stream()
//            .filter(it -> it.getUrl().equals(state.myUrl))
//            .collect(Collectors.toList());
//
//        if (nodesToUpdate.isEmpty()) {
//            return;
//        }
//
//        for (RepositoryNode node : nodesToUpdate) {
//            node.update();
//        }
//    }
}
