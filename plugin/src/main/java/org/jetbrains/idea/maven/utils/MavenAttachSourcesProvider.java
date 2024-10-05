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
package org.jetbrains.idea.maven.utils;

import com.intellij.java.impl.codeInsight.AttachSourcesProvider;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiFile;
import consulo.maven.MavenNotificationGroup;
import consulo.maven.rt.server.common.model.MavenArtifact;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationType;
import consulo.project.ui.notification.Notifications;
import consulo.ui.Component;
import consulo.ui.event.ComponentEvent;
import consulo.util.concurrent.AsyncResult;
import jakarta.inject.Inject;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.project.MavenArtifactDownloader;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.annotation.Nonnull;
import java.util.*;

@ExtensionImpl
public class MavenAttachSourcesProvider implements AttachSourcesProvider {
    private final Project myProject;

    @Inject
    public MavenAttachSourcesProvider(Project project) {
        myProject = project;
    }

    @Override
    @Nonnull
    @RequiredReadAction
    public Collection<AttachSourcesAction> getActions(final List<LibraryOrderEntry> orderEntries, final PsiFile psiFile) {
        Collection<MavenProject> projects = getMavenProjects(psiFile);
        if (projects.isEmpty()) {
            return Collections.emptyList();
        }

        if (findArtifacts(projects, orderEntries).isEmpty()) {
            return Collections.emptyList();
        }

        return List.of(new AttachSourcesAction() {
            @Override
            public String getName() {
                return MavenProjectLocalize.mavenActionDownloadSources().get();
            }

            @Override
            public String getBusyText() {
                return MavenProjectLocalize.mavenActionDownloadSourcesBusyText().get();
            }

            @Override
            @RequiredReadAction
            public AsyncResult<Void> perform(@Nonnull List<LibraryOrderEntry> list, @Nonnull ComponentEvent<Component> uiEvent) {
                // may have been changed by this time...
                Collection<MavenProject> mavenProjects = getMavenProjects(psiFile);
                if (mavenProjects.isEmpty()) {
                    return AsyncResult.rejected();
                }

                MavenProjectsManager manager = MavenProjectsManager.getInstance(psiFile.getProject());

                Collection<MavenArtifact> artifacts = findArtifacts(mavenProjects, orderEntries);
                if (artifacts.isEmpty()) {
                    return AsyncResult.rejected();
                }

                final AsyncResult<MavenArtifactDownloader.DownloadResult> result = AsyncResult.undefined();
                manager.scheduleArtifactsDownloading(mavenProjects, artifacts, true, false, result);

                AsyncResult<Void> resultWrapper = AsyncResult.undefined();

                result.doWhenDone(downloadResult -> {
                    if (!downloadResult.unresolvedSources.isEmpty()) {
                        String message = "<html>Sources not found for:";
                        int count = 0;
                        for (MavenId each : downloadResult.unresolvedSources) {
                            if (count++ > 5) {
                                message += "<br>and more...";
                                break;
                            }
                            message += "<br>" + each.getDisplayString();
                        }
                        message += "</html>";

                        final String finalMessage = message;
                        myProject.getApplication().invokeLater(() -> Notifications.Bus.notify(
                            new Notification(MavenNotificationGroup.ROOT,
                                "Cannot download sources",
                                finalMessage,
                                NotificationType.WARNING
                            ),
                            psiFile.getProject()
                        ));
                    }

                    if (downloadResult.resolvedSources.isEmpty()) {
                        resultWrapper.setRejected();
                    }
                    else {
                        resultWrapper.setDone();
                    }
                });

                return resultWrapper;
            }
        });
    }

    @RequiredReadAction
    private static Collection<MavenArtifact> findArtifacts(Collection<MavenProject> mavenProjects, List<LibraryOrderEntry> orderEntries) {
        Collection<MavenArtifact> artifacts = new HashSet<MavenArtifact>();
        for (MavenProject each : mavenProjects) {
            for (LibraryOrderEntry entry : orderEntries) {
                final MavenArtifact artifact = MavenRootModelAdapter.findArtifact(each, entry.getLibrary());
                if (artifact != null && !"system".equals(artifact.getScope())) {
                    artifacts.add(artifact);
                }
            }
        }
        return artifacts;
    }

    @RequiredReadAction
    private static Collection<MavenProject> getMavenProjects(PsiFile psiFile) {
        Project project = psiFile.getProject();
        Collection<MavenProject> result = new ArrayList<MavenProject>();
        for (OrderEntry each : ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(psiFile.getVirtualFile())) {
            MavenProject mavenProject = MavenProjectsManager.getInstance(project).findProject(each.getOwnerModule());
            if (mavenProject != null) {
                result.add(mavenProject);
            }
        }
        return result;
    }
}
