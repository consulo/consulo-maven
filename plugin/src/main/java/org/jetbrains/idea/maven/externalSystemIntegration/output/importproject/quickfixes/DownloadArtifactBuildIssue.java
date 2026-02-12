// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes;

import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.ui.ex.action.DataContext;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class DownloadArtifactBuildIssue {
    private DownloadArtifactBuildIssue() {
    }

    @Nonnull
    public static BuildIssue getIssue(@Nonnull String title, @Nonnull String errorMessage) {
        List<BuildIssueQuickFix> quickFixes = Collections.singletonList(new ForceUpdateSnapshotsImportQuickFix());

        String issueDescription = errorMessage + "\n\n" +
            MavenProjectBundle.message("maven.quickfix.cannot.artifact.download", ForceUpdateSnapshotsImportQuickFix.ID);

        return new BuildIssue() {
            @Nonnull
            @Override
            public String getTitle() {
                return title;
            }

            @Nonnull
            @Override
            public String getDescription() {
                return issueDescription;
            }

            @Nonnull
            @Override
            public List<BuildIssueQuickFix> getQuickFixes() {
                return quickFixes;
            }

            @Nullable
            @Override
            public Navigatable getNavigatable(@Nonnull Project project) {
                return null;
            }
        };
    }
}

class ForceUpdateSnapshotsImportQuickFix implements BuildIssueQuickFix {
    public static final String ID = "force_update_snapshots_import_quick_fix";

    @Nonnull
    @Override
    public String getId() {
        return ID;
    }

    @Nonnull
    @Override
    public CompletableFuture<?> runQuickFix(@Nonnull Project project, @Nonnull DataContext dataContext) {
        MavenUtil.shutdownMavenConnectors(project);
        MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
        manager.setForceUpdateSnapshots(true);
        manager.forceUpdateProjects();
        return CompletableFuture.completedFuture(null);
    }
}
