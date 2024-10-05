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
package org.jetbrains.idea.maven.utils.library;

import consulo.application.AccessToken;
import consulo.application.ApplicationManager;
import consulo.application.CommonBundle;
import consulo.application.WriteAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.util.function.Processor;
import consulo.content.OrderRootType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.base.DocumentationOrderRootType;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.library.NewLibraryConfiguration;
import consulo.content.library.OrderRoot;
import consulo.content.library.ui.LibraryEditor;
import consulo.maven.MavenNotificationGroup;
import consulo.maven.rt.server.common.model.*;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationType;
import consulo.project.ui.notification.Notifications;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.SmartList;
import consulo.util.io.FilePermissionCopier;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.function.PairProcessor;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.idea.maven.execution.SoutMavenConsole;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.services.MavenRepositoryServicesManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.RepositoryAttachDialog;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Gregory.Shrago
 */
public class RepositoryAttachHandler {
    @Nullable
    public static NewLibraryConfiguration chooseLibraryAndDownload(
        final @Nonnull Project project,
        final @Nullable String initialFilter,
        JComponent parentComponent
    ) {
        final RepositoryAttachDialog dialog = new RepositoryAttachDialog(project, false, initialFilter);
        dialog.setTitle("Download Library From Maven Repository");
        dialog.show();
        if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
            return null;
        }

        final String copyTo = dialog.getDirectoryPath();
        final String coord = dialog.getCoordinateText();
        final boolean attachJavaDoc = dialog.getAttachJavaDoc();
        final boolean attachSources = dialog.getAttachSources();
        final SmartList<MavenExtraArtifactType> extraTypes = new SmartList<>();
        if (attachSources) {
            extraTypes.add(MavenExtraArtifactType.SOURCES);
        }
        if (attachJavaDoc) {
            extraTypes.add(MavenExtraArtifactType.DOCS);
        }
        final Ref<NewLibraryConfiguration> result = Ref.create(null);
        resolveLibrary(
            project,
            coord,
            extraTypes,
            dialog.getRepositories(),
            new Processor<>() {
                public boolean process(final List<MavenArtifact> artifacts) {
                    if (!artifacts.isEmpty()) {
                        AccessToken accessToken = WriteAction.start();
                        try {
                            final List<OrderRoot> roots = createRoots(artifacts, copyTo);
                            result.set(new NewLibraryConfiguration(
                                coord,
                                RepositoryLibraryType.getInstance(),
                                new RepositoryLibraryProperties(coord)
                            ) {
                                @Override
                                public void addRoots(@Nonnull LibraryEditor editor) {
                                    editor.addRoots(roots);
                                }
                            });
                        }
                        finally {
                            accessToken.finish();
                        }

                        final StringBuilder sb = new StringBuilder();
                        final String title = "The following files were downloaded:";
                        sb.append("<ol>");
                        for (MavenArtifact each : artifacts) {
                            sb.append("<li>");
                            sb.append(each.getFile().getName());
                            final String scope = each.getScope();
                            if (scope != null) {
                                sb.append(" (");
                                sb.append(scope);
                                sb.append(")");
                            }
                            sb.append("</li>");
                        }
                        sb.append("</ol>");
                        Notifications.Bus.notify(new Notification(
                            MavenNotificationGroup.REPOSITORY,
                            title,
                            sb.toString(),
                            NotificationType.INFORMATION
                        ), project);
                    }
                    return true;
                }
            }
        );

        NewLibraryConfiguration configuration = result.get();
        if (configuration == null) {
            Messages.showErrorDialog(parentComponent, "No files were downloaded for " + coord, CommonBundle.getErrorTitle());
        }
        return configuration;
    }

    private static List<OrderRoot> createRoots(Collection<MavenArtifact> artifacts, String copyTo) {
        final List<OrderRoot> result = new ArrayList<OrderRoot>();
        final VirtualFileManager manager = VirtualFileManager.getInstance();
        for (MavenArtifact each : artifacts) {
            try {
                File repoFile = each.getFile();
                File toFile = repoFile;
                if (copyTo != null) {
                    toFile = new File(copyTo, repoFile.getName());
                    if (repoFile.exists()) {
                        FileUtil.copy(repoFile, toFile, FilePermissionCopier.BY_NIO2);
                    }
                }
                // search for jar file first otherwise lib root won't be found!
                manager.refreshAndFindFileByUrl(VirtualFileUtil.pathToUrl(FileUtil.toSystemIndependentName(toFile.getPath())));
                final String url = VirtualFileUtil.getUrlForLibraryRoot(toFile);
                final VirtualFile file = manager.refreshAndFindFileByUrl(url);
                if (file != null) {
                    OrderRootType rootType;
                    if (MavenExtraArtifactType.DOCS.getDefaultClassifier().equals(each.getClassifier())) {
                        rootType = DocumentationOrderRootType.getInstance();
                    }
                    else if (MavenExtraArtifactType.SOURCES.getDefaultClassifier().equals(each.getClassifier())) {
                        rootType = SourcesOrderRootType.getInstance();
                    }
                    else {
                        rootType = BinariesOrderRootType.getInstance();
                    }
                    result.add(new OrderRoot(file, rootType));
                }
            }
            catch (MalformedURLException e) {
                MavenLog.LOG.warn(e);
            }
            catch (IOException e) {
                MavenLog.LOG.warn(e);
            }
        }
        return result;
    }

    public static void searchArtifacts(
        final Project project, String coord,
        final PairProcessor<Collection<Pair<MavenArtifactInfo, MavenRepositoryInfo>>, Boolean> resultProcessor
    ) {
        if (coord == null || coord.length() == 0) {
            return;
        }
        final MavenArtifactInfo template;
        if (coord.indexOf(':') == -1 && Character.isUpperCase(coord.charAt(0))) {
            template = new MavenArtifactInfo(null, null, null, "jar", null, coord, null);
        }
        else {
            template = new MavenArtifactInfo(getMavenId(coord), "jar", null);
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {
            public void run(@Nonnull ProgressIndicator indicator) {
                String[] urls = MavenRepositoryServicesManager.getServiceUrls();
                boolean tooManyResults = false;
                final AtomicBoolean proceedFlag = new AtomicBoolean(true);

                for (int i = 0, length = urls.length; i < length; i++) {
                    if (!proceedFlag.get()) {
                        break;
                    }
                    final List<Pair<MavenArtifactInfo, MavenRepositoryInfo>> resultList = new ArrayList<>();
                    try {
                        String serviceUrl = urls[i];
                        final List<MavenArtifactInfo> artifacts;
                        artifacts = MavenRepositoryServicesManager.findArtifacts(template, serviceUrl);
                        if (!artifacts.isEmpty()) {
                            if (!proceedFlag.get()) {
                                break;
                            }
                            final List<MavenRepositoryInfo> repositories = MavenRepositoryServicesManager.getRepositories(serviceUrl);
                            final HashMap<String, MavenRepositoryInfo> map = new HashMap<String, MavenRepositoryInfo>();
                            for (MavenRepositoryInfo repository : repositories) {
                                map.put(repository.getId(), repository);
                            }
                            for (MavenArtifactInfo artifact : artifacts) {
                                if (artifact == null) {
                                    tooManyResults = true;
                                }
                                else {
                                    resultList.add(Pair.create(artifact, map.get(artifact.getRepositoryId())));
                                }
                            }
                        }
                    }
                    catch (Exception e) {
                        MavenLog.LOG.error(e);
                    }
                    finally {
                        if (!proceedFlag.get()) {
                            break;
                        }
                        final Boolean aBoolean = i == length - 1 ? tooManyResults : null;
                        ApplicationManager.getApplication().invokeLater(
                            new Runnable() {
                                public void run() {
                                    proceedFlag.set(resultProcessor.process(resultList, aBoolean));
                                }
                            },
                            () -> !proceedFlag.get()
                        );
                    }
                }
            }
        });
    }

    public static void searchRepositories(
        final Project project,
        final Collection<String> nexusUrls,
        final Processor<Collection<MavenRepositoryInfo>> resultProcessor
    ) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {

            public void run(@Nonnull ProgressIndicator indicator) {
                final Ref<List<MavenRepositoryInfo>> result = Ref.create(Collections.<MavenRepositoryInfo>emptyList());
                try {
                    final ArrayList<MavenRepositoryInfo> repoList = new ArrayList<>();
                    for (String nexusUrl : nexusUrls) {
                        final List<MavenRepositoryInfo> repositories;
                        try {
                            repositories = MavenRepositoryServicesManager.getRepositories(nexusUrl);
                        }
                        catch (Exception ex) {
                            MavenLog.LOG.warn("Accessing Service at: " + nexusUrl, ex);
                            continue;
                        }
                        repoList.addAll(repositories);
                    }
                    result.set(repoList);
                }
                catch (Exception e) {
                    MavenLog.LOG.error(e);
                }
                finally {
                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                        public void run() {
                            resultProcessor.process(result.get());
                        }
                    });
                }
            }
        });
    }

    private static void resolveLibrary(
        final Project project,
        final String coord,
        final List<MavenExtraArtifactType> extraTypes,
        final Collection<MavenRepositoryInfo> repositories,
        final Processor<List<MavenArtifact>> resultProcessor
    ) {
        final MavenId mavenId = getMavenId(coord);
        final Task task = new Task.Modal(project, "Maven", false) {
            public void run(@Nonnull ProgressIndicator indicator) {
                doResolveInner(project, mavenId, extraTypes, repositories, resultProcessor, indicator);
            }
        };
        ProgressManager.getInstance().run(task);
    }

    private static void doResolveInner(
        Project project,
        MavenId mavenId,
        List<MavenExtraArtifactType> extraTypes,
        Collection<MavenRepositoryInfo> repositories,
        final Processor<List<MavenArtifact>> resultProcessor,
        ProgressIndicator indicator
    ) {
        boolean cancelled = false;
        final Collection<MavenArtifact> result = new LinkedHashSet<>();
        MavenEmbeddersManager manager = MavenProjectsManager.getInstance(project).getEmbeddersManager();
        MavenEmbedderWrapper embedder = manager.getEmbedder(MavenEmbeddersManager.FOR_DOWNLOAD);
        try {
            embedder.customizeForResolve(new SoutMavenConsole(), new MavenProgressIndicator(indicator));
            final List<MavenRemoteRepository> remoteRepositories = convertRepositories(repositories);
            final List<MavenArtifact> firstResult = embedder.resolveTransitively(
                Collections.singletonList(new MavenArtifactInfo(mavenId, "jar", null)), remoteRepositories);
            for (MavenArtifact artifact : firstResult) {
                if (!artifact.isResolved()) {
                    continue;
                }
                if (MavenConstants.SCOPE_TEST.equals(artifact.getScope())) {
                    continue;
                }
                result.add(artifact);
            }
            // download docs & sources
            if (!extraTypes.isEmpty()) {
                final HashSet<String> allowedClassifiers = new HashSet<>();
                final Collection<MavenArtifactInfo> resolve = new LinkedHashSet<>();
                for (MavenExtraArtifactType extraType : extraTypes) {
                    allowedClassifiers.add(extraType.getDefaultClassifier());
                    resolve.add(new MavenArtifactInfo(mavenId, extraType.getDefaultExtension(), extraType.getDefaultClassifier()));
                    for (MavenArtifact artifact : firstResult) {
                        if (MavenConstants.SCOPE_TEST.equals(artifact.getScope())) {
                            continue;
                        }
                        resolve.add(new MavenArtifactInfo(
                            artifact.getMavenId(),
                            extraType.getDefaultExtension(),
                            extraType.getDefaultClassifier()
                        ));
                    }
                }
                final List<MavenArtifact> secondResult = embedder.resolveTransitively(new ArrayList<>(resolve), remoteRepositories);
                for (MavenArtifact artifact : secondResult) {
                    if (!artifact.isResolved()) {
                        continue;
                    }
                    if (MavenConstants.SCOPE_TEST.equals(artifact.getScope())) {
                        continue;
                    }
                    if (!allowedClassifiers.contains(artifact.getClassifier())) {
                        continue;
                    }
                    result.add(artifact);
                }
            }
        }
        catch (MavenProcessCanceledException e) {
            cancelled = true;
        }
        finally {
            manager.release(embedder);
            if (!cancelled) {
                ApplicationManager.getApplication().invokeAndWait(
                    new Runnable() {
                        public void run() {
                            resultProcessor.process(new ArrayList<>(result));
                        }
                    },
                    indicator.getModalityState()
                );
            }
        }
    }

    private static List<MavenRemoteRepository> convertRepositories(Collection<MavenRepositoryInfo> infos) {
        List<MavenRemoteRepository> result = new ArrayList<>(infos.size());
        for (MavenRepositoryInfo each : infos) {
            if (each.getUrl() != null) {
                result.add(new MavenRemoteRepository(each.getId(), each.getName(), each.getUrl(), null, null, null));
            }
        }
        return result;
    }

    public static MavenId getMavenId(final String coord) {
        final String[] parts = coord.split(":");
        return new MavenId(
            parts.length > 0 ? parts[0] : null,
            parts.length > 1 ? parts[1] : null,
            parts.length > 2 ? parts[2] : null
        );
    }
}
