// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes;

import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.build.issue.quickfix.OpenFileQuickFix;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.find.FindModel;
import com.intellij.find.findInProject.FindInProjectManager;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.ui.ex.action.DataContext;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenSettingsQuickFix;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.project.MavenSettingsCache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class RepositoryBlockedSyncIssue {
    private RepositoryBlockedSyncIssue() {
    }

    @Nonnull
    public static BuildIssue getIssue(@Nonnull Project project, @Nonnull String title) {
        List<BuildIssueQuickFix> quickFixes = new ArrayList<>();
        StringBuilder issueDescription = new StringBuilder(title);
        issueDescription.append("\n\n")
            .append(SyncBundle.message("maven.sync.quickfixes.repository.blocked"))
            .append("\n");

        Path settingsFile = MavenSettingsCache.getInstance(project).getEffectiveUserSettingsFile();
        OpenFileQuickFix openSettingsXmlQuickFix = null;
        if (settingsFile != null && Files.exists(settingsFile)) {
            openSettingsXmlQuickFix = new OpenFileQuickFix(settingsFile, null);
            quickFixes.add(openSettingsXmlQuickFix);
            issueDescription
                .append(SyncBundle.message("maven.sync.quickfixes.repository.blocked.show.settings", openSettingsXmlQuickFix.getId()))
                .append("\n");
        }

        UrlFilter urlFilter = new UrlFilter();
        var filterResult = urlFilter.applyFilter(title, title.length());
        List<String> repoUrls = new ArrayList<>();
        if (filterResult != null && filterResult.getResultItems() != null) {
            repoUrls = filterResult.getResultItems().stream()
                .filter(item -> item != null)
                .map(item -> title.substring(item.getHighlightStartOffset(), item.getHighlightEndOffset()))
                .filter(url -> !url.contains("0.0.0.0"))
                .collect(Collectors.toList());
        }

        for (String url : repoUrls) {
            FindBlockedRepositoryQuickFix quickFix = new FindBlockedRepositoryQuickFix(url);
            quickFixes.add(quickFix);
            issueDescription
                .append(SyncBundle.message("maven.sync.quickfixes.repository.blocked.find.repository", quickFix.getId(), url))
                .append("\n");
        }

        String repoUrlsJoined = String.join(",", repoUrls);
        String openSettingsId = openSettingsXmlQuickFix != null ? openSettingsXmlQuickFix.getId() : "";
        issueDescription
            .append(SyncBundle.message("maven.sync.quickfixes.repository.blocked.add.mirror", repoUrlsJoined, openSettingsId))
            .append("\n");

        OpenMavenSettingsQuickFix openMavenSettingsQuickFix = new OpenMavenSettingsQuickFix();
        quickFixes.add(openMavenSettingsQuickFix);
        issueDescription
            .append(SyncBundle.message("maven.sync.quickfixes.repository.blocked.downgrade", openMavenSettingsQuickFix.getId()))
            .append("\n");

        String finalDescription = issueDescription.toString();
        List<BuildIssueQuickFix> finalQuickFixes = quickFixes;

        return new BuildIssue() {
            @Nonnull
            @Override
            public String getTitle() {
                return SyncBundle.message("maven.sync.quickfixes.repository.blocked.title");
            }

            @Nonnull
            @Override
            public String getDescription() {
                return finalDescription;
            }

            @Nonnull
            @Override
            public List<BuildIssueQuickFix> getQuickFixes() {
                return finalQuickFixes;
            }

            @Nullable
            @Override
            public Navigatable getNavigatable(@Nonnull Project project) {
                return null;
            }
        };
    }
}

class FindBlockedRepositoryQuickFix implements BuildIssueQuickFix {
    private final String repoUrl;

    FindBlockedRepositoryQuickFix(@Nonnull String repoUrl) {
        this.repoUrl = repoUrl;
    }

    @Nonnull
    @Override
    public String getId() {
        return "maven_find_blocked_repository_quick_fix_" + repoUrl;
    }

    @Nonnull
    @Override
    public CompletableFuture<?> runQuickFix(@Nonnull Project project, @Nonnull DataContext dataContext) {
        FindModel findModel = new FindModel();
        findModel.setStringToFind(repoUrl);
        findModel.setProjectScope(true);
        FindInProjectManager.getInstance(project).findInProject(dataContext, findModel);
        return CompletableFuture.completedFuture(null);
    }
}
