/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.component.persist.*;
import consulo.component.util.ModificationTracker;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.document.FileDocumentManager;
import consulo.language.util.ModuleUtilCore;
import consulo.maven.module.extension.MavenModuleExtension;
import consulo.maven.rt.server.common.model.*;
import consulo.maven.rt.server.common.server.NativeMavenProjectHolder;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.project.startup.StartupManager;
import consulo.project.ui.notification.NotificationGroup;
import consulo.proxy.EventDispatcher;
import consulo.ui.ex.awt.util.Alarm;
import consulo.ui.ex.awt.util.Update;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Lists;
import consulo.util.concurrent.AsyncResult;
import consulo.util.io.FileUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.importing.MavenDefaultModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenFoldersImporter;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenMergingUpdateQueue;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;
import org.jetbrains.idea.maven.utils.MavenUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Singleton
@State(name = "MavenProjectsManager", storages = @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/misc.xml"))
@ServiceAPI(value = ComponentScope.PROJECT)
@ServiceImpl
public class MavenProjectsManager extends MavenSimpleProjectComponent implements PersistentStateComponent<MavenProjectsManagerState>, SettingsSavingComponent, Disposable {
    private static final int IMPORT_DELAY = 1000;
    private static final String NON_MANAGED_POM_NOTIFICATION_GROUP_ID = "Maven: non-managed pom.xml";
    private static final NotificationGroup NON_MANAGED_POM_NOTIFICATION_GROUP =
        NotificationGroup.balloonGroup(NON_MANAGED_POM_NOTIFICATION_GROUP_ID);

    private final ReentrantLock initLock = new ReentrantLock();
    private final AtomicBoolean isInitialized = new AtomicBoolean();

    private MavenProjectsManagerState myState = new MavenProjectsManagerState();

    private final Alarm myInitializationAlarm;

    private final MavenEmbeddersManager myEmbeddersManager;

    private MavenProjectsTree myProjectsTree;
    private MavenProjectsManagerWatcher myWatcher;

    private MavenProjectsProcessor myReadingProcessor;
    private MavenProjectsProcessor myResolvingProcessor;
    private MavenProjectsProcessor myPluginsResolvingProcessor;
    private MavenProjectsProcessor myFoldersResolvingProcessor;
    private MavenProjectsProcessor myArtifactsDownloadingProcessor;
    private MavenProjectsProcessor myPostProcessor;

    private MavenMergingUpdateQueue myImportingQueue;
    private final Object myImportingDataLock = new Object();
    private final Map<MavenProject, MavenProjectChanges> myProjectsToImport = new LinkedHashMap<>();
    private final Set<MavenProject> myProjectsToResolve = new LinkedHashSet<>();

    private boolean myImportModuleGroupsRequired = false;

    private final EventDispatcher<MavenProjectsTree.Listener> myProjectsTreeDispatcher =
        EventDispatcher.create(MavenProjectsTree.Listener.class);
    private final List<Listener> myManagerListeners = Lists.newLockFreeCopyOnWriteList();
    private ModificationTracker myModificationTracker;

    private MavenWorkspaceSettings myWorkspaceSettings;

    public static MavenProjectsManager getInstance(Project p) {
        return p.getComponent(MavenProjectsManager.class);
    }

    @Inject
    public MavenProjectsManager(Project project) {
        super(project);
        myEmbeddersManager = new MavenEmbeddersManager(myProject);
        myModificationTracker = new MavenModificationTracker(this);
        myInitializationAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myProject);
    }

    @Override
    public MavenProjectsManagerState getState() {
        if (isInitialized()) {
            applyTreeToState();
        }
        return myState;
    }

    @Override
    public void loadState(MavenProjectsManagerState state) {
        myState = state;
        if (isInitialized()) {
            applyStateToTree();
            scheduleUpdateAllProjects(false);
        }
    }

    public ModificationTracker getModificationTracker() {
        return myModificationTracker;
    }

    public MavenGeneralSettings getGeneralSettings() {
        return getWorkspaceSettings().generalSettings;
    }

    public MavenImportingSettings getImportingSettings() {
        return getWorkspaceSettings().importingSettings;
    }

    private MavenWorkspaceSettings getWorkspaceSettings() {
        if (myWorkspaceSettings == null) {
            myWorkspaceSettings = MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings();
        }

        return myWorkspaceSettings;
    }

    public File getLocalRepository() {
        return getGeneralSettings().getEffectiveLocalRepository();
    }

    public void doInit() {
        boolean wasMavenized = !myState.originalFiles.isEmpty();
        if (!wasMavenized) {
            return;
        }
        initMavenized();
    }

    private void initMavenized() {
        doInit(false);
    }

    private void initNew(List<VirtualFile> files, MavenExplicitProfiles explicitProfiles) {
        myState.originalFiles = MavenUtil.collectPaths(files);
        getWorkspaceSettings().setEnabledProfiles(explicitProfiles.getEnabledProfiles());
        getWorkspaceSettings().setDisabledProfiles(explicitProfiles.getDisabledProfiles());
        doInit(true);
    }

    @TestOnly
    public void initForTests() {
        doInit(false);
    }

    private void doInit(final boolean isNew) {
        initLock.lock();

        try {
            if (isInitialized.getAndSet(true)) {
                return;
            }

            initProjectsTree(!isNew);

            initWorkers();
            listenForSettingsChanges();
            listenForProjectsTreeChanges();

            MavenUtil.runWhenInitialized(
                myProject,
                () -> {
                    if (!isUnitTestMode()) {
                        fireActivated();
                        listenForExternalChanges();
                    }
                    scheduleUpdateAllProjects(isNew);
                }
            );
        }
        finally {
            initLock.unlock();
        }
    }

    private void initProjectsTree(boolean tryToLoadExisting) {
        if (tryToLoadExisting) {
            File file = getProjectsTreeFile();
            try {
                if (file.exists()) {
                    myProjectsTree = MavenProjectsTree.read(file);
                }
            }
            catch (IOException e) {
                MavenLog.LOG.info(e);
            }
        }

        if (myProjectsTree == null) {
            myProjectsTree = new MavenProjectsTree();
        }
        applyStateToTree();
        myProjectsTree.addListener(myProjectsTreeDispatcher.getMulticaster());
    }

    private void applyTreeToState() {
        myState.originalFiles = myProjectsTree.getManagedFilesPaths();
        myState.ignoredFiles = new HashSet<>(myProjectsTree.getIgnoredFilesPaths());
        myState.ignoredPathMasks = myProjectsTree.getIgnoredFilesPatterns();
    }

    private void applyStateToTree() {
        MavenWorkspaceSettings settings = getWorkspaceSettings();
        MavenExplicitProfiles explicitProfiles = new MavenExplicitProfiles(settings.enabledProfiles, settings.disabledProfiles);
        myProjectsTree.resetManagedFilesPathsAndProfiles(myState.originalFiles, explicitProfiles);
        myProjectsTree.setIgnoredFilesPaths(new ArrayList<>(myState.ignoredFiles));
        myProjectsTree.setIgnoredFilesPatterns(myState.ignoredPathMasks);
    }

    @Override
    public void save() {
        if (myProjectsTree != null) {
            try {
                myProjectsTree.save(getProjectsTreeFile());
            }
            catch (IOException e) {
                MavenLog.LOG.info(e);
            }
        }
    }

    private File getProjectsTreeFile() {
        return new File(getProjectsTreesDir(), myProject.getLocationHash() + "/tree.dat");
    }

    private static File getProjectsTreesDir() {
        return MavenUtil.getPluginSystemDir("Projects");
    }

    private void initWorkers() {
        myReadingProcessor = new MavenProjectsProcessor(myProject, MavenProjectLocalize.mavenReading().get(), false, myEmbeddersManager);
        myResolvingProcessor = new MavenProjectsProcessor(myProject, MavenProjectLocalize.mavenResolving().get(), true, myEmbeddersManager);
        myPluginsResolvingProcessor =
            new MavenProjectsProcessor(myProject, MavenProjectLocalize.mavenDownloadingPlugins().get(), true, myEmbeddersManager);
        myFoldersResolvingProcessor =
            new MavenProjectsProcessor(myProject, MavenProjectLocalize.mavenUpdatingFolders().get(), true, myEmbeddersManager);
        myArtifactsDownloadingProcessor =
            new MavenProjectsProcessor(myProject, MavenProjectLocalize.mavenDownloading().get(), true, myEmbeddersManager);
        myPostProcessor = new MavenProjectsProcessor(myProject, MavenProjectLocalize.mavenPostProcessing().get(), true, myEmbeddersManager);

        myWatcher =
            new MavenProjectsManagerWatcher(myProject, this, myProjectsTree, getGeneralSettings(), myReadingProcessor, myEmbeddersManager);

        myImportingQueue = new MavenMergingUpdateQueue("MavenProjectsManager: Importing queue", IMPORT_DELAY, !isUnitTestMode(), myProject);
        myImportingQueue.setPassThrough(false);

        myImportingQueue.makeUserAware(myProject);
        myImportingQueue.makeModalAware(myProject);
    }

    private void listenForSettingsChanges() {
        getImportingSettings().addListener(new MavenImportingSettings.Listener() {
            @Override
            public void autoImportChanged() {
                if (myProject.isDisposed()) {
                    return;
                }

                if (getImportingSettings().isImportAutomatically()) {
                    scheduleImportAndResolve();
                }
            }

            @Override
            public void createModuleGroupsChanged() {
                scheduleImportSettings(true);
            }

            @Override
            public void createModuleForAggregatorsChanged() {
                scheduleImportSettings();
            }
        });
    }

    private void listenForProjectsTreeChanges() {
        myProjectsTree.addListener(new MavenProjectsTree.ListenerAdapter() {
            @Override
            public void projectsIgnoredStateChanged(List<MavenProject> ignored, List<MavenProject> unignored, boolean fromImport) {
                if (!fromImport) {
                    scheduleImport();
                }
            }

            @Override
            public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
                myEmbeddersManager.clearCaches();

                unscheduleAllTasks(deleted);

                List<MavenProject> updatedProjects = MavenUtil.collectFirsts(updated);

                // import only updated projects and dependents of them (we need to update faced-deps, packaging etc);
                List<Pair<MavenProject, MavenProjectChanges>> toImport = new ArrayList<>(updated);

                for (MavenProject eachDependent : myProjectsTree.getDependentProjects(updatedProjects)) {
                    toImport.add(Pair.create(eachDependent, MavenProjectChanges.DEPENDENCIES));
                }

                // resolve updated, theirs dependents, and dependents of deleted
                Set<MavenProject> toResolve = new HashSet<>(updatedProjects);
                toResolve.addAll(myProjectsTree.getDependentProjects(ContainerUtil.concat(updatedProjects, deleted)));

                // do not try to resolve projects with syntactic errors
                Iterator<MavenProject> it = toResolve.iterator();
                while (it.hasNext()) {
                    MavenProject each = it.next();
                    if (each.hasReadingProblems()) {
                        it.remove();
                    }
                }

                if (haveChanges(toImport) || !deleted.isEmpty()) {
                    scheduleForNextImport(toImport);
                }

                if (!deleted.isEmpty() && !hasScheduledProjects()) {
                    MavenProject project = ObjectUtil.chooseNotNull(
                        ContainerUtil.getFirstItem(toResolve),
                        ContainerUtil.getFirstItem(getNonIgnoredProjects())
                    );
                    if (project != null) {
                        scheduleForNextImport(Pair.create(project, MavenProjectChanges.ALL));
                        scheduleForNextResolve(ContainerUtil.list(project));
                    }
                }

                scheduleForNextResolve(toResolve);

                fireProjectScheduled();
            }

            private boolean haveChanges(List<Pair<MavenProject, MavenProjectChanges>> projectsWithChanges) {
                for (MavenProjectChanges each : MavenUtil.collectSeconds(projectsWithChanges)) {
                    if (each.hasChanges()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void projectResolved(
                Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                @Nullable NativeMavenProjectHolder nativeMavenProject
            ) {
                if (nativeMavenProject != null) {
                    if (shouldScheduleProject(projectWithChanges)) {
                        scheduleForNextImport(projectWithChanges);

                        MavenImportingSettings importingSettings =
                            ReadAction.compute(() -> myProject.isDisposed() ? null : getImportingSettings());

                        if (importingSettings == null) {
                            return;
                        }

                        scheduleArtifactsDownloading(
                            Collections.singleton(projectWithChanges.first),
                            null,
                            importingSettings.isDownloadSourcesAutomatically(),
                            importingSettings.isDownloadDocsAutomatically(),
                            null
                        );
                    }

                    if (!projectWithChanges.first.hasReadingProblems() && projectWithChanges.first.hasUnresolvedPlugins()) {
                        schedulePluginsResolve(projectWithChanges.first, nativeMavenProject);
                    }
                }
            }

            @Override
            public void foldersResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
                if (shouldScheduleProject(projectWithChanges)) {
                    scheduleForNextImport(projectWithChanges);
                }
            }

            private boolean shouldScheduleProject(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
                return !projectWithChanges.first.hasReadingProblems() && projectWithChanges.second.hasChanges();
            }
        });
    }

    public void listenForExternalChanges() {
        myWatcher.start();
    }

    @Override
    public void dispose() {
        initLock.lock();
        try {
            if (!isInitialized.getAndSet(false)) {
                return;
            }

            Disposer.dispose(myImportingQueue);

            myWatcher.stop();

            myReadingProcessor.stop();
            myResolvingProcessor.stop();
            myPluginsResolvingProcessor.stop();
            myFoldersResolvingProcessor.stop();
            myArtifactsDownloadingProcessor.stop();
            myPostProcessor.stop();

            if (isUnitTestMode()) {
                FileUtil.delete(getProjectsTreesDir());
            }
        }
        finally {
            initLock.unlock();
        }
    }

    public MavenEmbeddersManager getEmbeddersManager() {
        return myEmbeddersManager;
    }

    private boolean isInitialized() {
        return !initLock.isLocked() && isInitialized.get();
    }

    public boolean isMavenizedProject() {
        return isInitialized();
    }

    public boolean isMavenizedModule(@Nonnull final Module m) {
        return ReadAction.compute(() -> ModuleUtilCore.getExtension(m, MavenModuleExtension.class) != null);
    }

    @TestOnly
    public void resetManagedFilesAndProfilesInTests(List<VirtualFile> files, MavenExplicitProfiles profiles) {
        myWatcher.resetManagedFilesAndProfilesInTests(files, profiles);
    }

    public void addManagedFilesWithProfiles(List<VirtualFile> files, MavenExplicitProfiles profiles) {
        if (!isInitialized()) {
            initNew(files, profiles);
        }
        else {
            myWatcher.addManagedFilesWithProfiles(files, profiles);
        }
    }

    public void addManagedFiles(@Nonnull List<VirtualFile> files) {
        addManagedFilesWithProfiles(files, MavenExplicitProfiles.NONE);
    }

    public void addManagedFilesOrUnignore(@Nonnull List<VirtualFile> files) {
        removeIgnoredFilesPaths(MavenUtil.collectPaths(files));
        addManagedFiles(files);
    }

    public void removeManagedFiles(@Nonnull List<VirtualFile> files) {
        myWatcher.removeManagedFiles(files);
    }

    public boolean isManagedFile(@Nonnull VirtualFile f) {
        if (!isInitialized()) {
            return false;
        }
        return myProjectsTree.isManagedFile(f);
    }

    @Nonnull
    public MavenExplicitProfiles getExplicitProfiles() {
        if (!isInitialized()) {
            return MavenExplicitProfiles.NONE;
        }
        return myProjectsTree.getExplicitProfiles();
    }

    public void setExplicitProfiles(@Nonnull MavenExplicitProfiles profiles) {
        myWatcher.setExplicitProfiles(profiles);
    }

    @Nonnull
    public Collection<String> getAvailableProfiles() {
        if (!isInitialized()) {
            return Collections.emptyList();
        }
        return myProjectsTree.getAvailableProfiles();
    }

    @Nonnull
    public Collection<Pair<String, MavenProfileKind>> getProfilesWithStates() {
        if (!isInitialized()) {
            return Collections.emptyList();
        }
        return myProjectsTree.getProfilesWithStates();
    }

    public boolean hasProjects() {
        return isInitialized() && myProjectsTree.hasProjects();
    }

    @Nonnull
    public List<MavenProject> getProjects() {
        if (!isInitialized()) {
            return Collections.emptyList();
        }
        return myProjectsTree.getProjects();
    }

    @Nonnull
    public List<MavenProject> getRootProjects() {
        if (!isInitialized()) {
            return Collections.emptyList();
        }
        return myProjectsTree.getRootProjects();
    }

    @Nonnull
    public List<MavenProject> getNonIgnoredProjects() {
        if (!isInitialized()) {
            return Collections.emptyList();
        }
        return myProjectsTree.getNonIgnoredProjects();
    }

    @Nonnull
    public List<VirtualFile> getProjectsFiles() {
        if (!isInitialized()) {
            return Collections.emptyList();
        }
        return myProjectsTree.getProjectsFiles();
    }

    @Nullable
    public MavenProject findProject(@Nonnull VirtualFile f) {
        if (!isInitialized()) {
            return null;
        }
        return myProjectsTree.findProject(f);
    }

    @Nullable
    public MavenProject findProject(@Nonnull MavenId id) {
        if (!isInitialized()) {
            return null;
        }
        return myProjectsTree.findProject(id);
    }

    @Nullable
    public MavenProject findProject(@Nonnull MavenArtifact artifact) {
        if (!isInitialized()) {
            return null;
        }
        return myProjectsTree.findProject(artifact);
    }

    @Nullable
    public MavenProject findProject(@Nonnull Module module) {
        VirtualFile f = findPomFile(module, new MavenModelsProvider() {
            @Override
            public Module[] getModules() {
                throw new UnsupportedOperationException();
            }

            @Override
            public VirtualFile[] getContentRoots(Module module) {
                return ModuleRootManager.getInstance(module).getContentRoots();
            }
        });
        return f == null ? null : findProject(f);
    }

    @Nullable
    public Module findModule(@Nonnull MavenProject project) {
        if (!isInitialized()) {
            return null;
        }
        return ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(project.getFile());
    }

    @Nonnull
    public Collection<MavenProject> findInheritors(@Nullable MavenProject parent) {
        if (parent == null || !isInitialized()) {
            return Collections.emptyList();
        }
        return myProjectsTree.findInheritors(parent);
    }

    @Nullable
    public MavenProject findContainingProject(@Nonnull VirtualFile file) {
        if (!isInitialized()) {
            return null;
        }
        Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file);
        return module == null ? null : findProject(module);
    }

    @Nullable
    private static VirtualFile findPomFile(@Nonnull Module module, @Nonnull MavenModelsProvider modelsProvider) {
        for (VirtualFile root : modelsProvider.getContentRoots(module)) {
            final VirtualFile virtualFile = root.findChild(MavenConstants.POM_XML);
            if (virtualFile != null) {
                return virtualFile;
            }
        }
        return null;
    }

    @Nullable
    public MavenProject findAggregator(@Nonnull MavenProject module) {
        if (!isInitialized()) {
            return null;
        }
        return myProjectsTree.findAggregator(module);
    }

    @Nonnull
    public List<MavenProject> getModules(@Nonnull MavenProject aggregator) {
        if (!isInitialized()) {
            return Collections.emptyList();
        }
        return myProjectsTree.getModules(aggregator);
    }

    @Nonnull
    public List<String> getIgnoredFilesPaths() {
        if (!isInitialized()) {
            return Collections.emptyList();
        }
        return myProjectsTree.getIgnoredFilesPaths();
    }

    public void setIgnoredFilesPaths(@Nonnull List<String> paths) {
        if (!isInitialized()) {
            return;
        }
        myProjectsTree.setIgnoredFilesPaths(paths);
    }

    public void removeIgnoredFilesPaths(final Collection<String> paths) {
        if (!isInitialized()) {
            return;
        }
        myProjectsTree.removeIgnoredFilesPaths(paths);
    }

    public boolean getIgnoredState(@Nonnull MavenProject project) {
        return isInitialized() && myProjectsTree.getIgnoredState(project);
    }

    public void setIgnoredState(@Nonnull List<MavenProject> projects, boolean ignored) {
        if (!isInitialized()) {
            return;
        }
        myProjectsTree.setIgnoredState(projects, ignored);
    }

    @Nonnull
    public List<String> getIgnoredFilesPatterns() {
        if (!isInitialized()) {
            return Collections.emptyList();
        }
        return myProjectsTree.getIgnoredFilesPatterns();
    }

    public void setIgnoredFilesPatterns(@Nonnull List<String> patterns) {
        if (!isInitialized()) {
            return;
        }
        myProjectsTree.setIgnoredFilesPatterns(patterns);
    }

    public boolean isIgnored(@Nonnull MavenProject project) {
        return isInitialized() && myProjectsTree.isIgnored(project);
    }

    public Set<MavenRemoteRepository> getRemoteRepositories() {
        Set<MavenRemoteRepository> result = new HashSet<>();
        for (MavenProject each : getProjects()) {
            for (MavenRemoteRepository eachRepository : each.getRemoteRepositories()) {
                result.add(eachRepository);
            }
        }
        return result;
    }

    @TestOnly
    public MavenProjectsTree getProjectsTreeForTests() {
        return myProjectsTree;
    }

    private void scheduleUpdateAllProjects(boolean forceImportAndResolve) {
        doScheduleUpdateProjects(null, false, forceImportAndResolve);
    }

    public AsyncResult<Void> forceUpdateProjects(@Nonnull Collection<MavenProject> projects) {
        return doScheduleUpdateProjects(projects, true, true);
    }

    public void forceUpdateAllProjectsOrFindAllAvailablePomFiles() {
        if (!isMavenizedProject()) {
            addManagedFiles(collectAllAvailablePomFiles());
        }
        doScheduleUpdateProjects(null, true, true);
    }

    private AsyncResult<Void> doScheduleUpdateProjects(
        final Collection<MavenProject> projects,
        final boolean forceUpdate,
        final boolean forceImportAndResolve
    ) {
        final AsyncResult<Void> promise = new AsyncResult<>();
        MavenUtil.runWhenInitialized(
            myProject,
            () -> {
                if (projects == null) {
                    myWatcher.scheduleUpdateAll(forceUpdate, forceImportAndResolve).notify(promise);
                }
                else {
                    myWatcher.scheduleUpdate(
                        MavenUtil.collectFiles(projects),
                        Collections.<VirtualFile>emptyList(),
                        forceUpdate,
                        forceImportAndResolve
                    ).notify(promise);
                }
            }
        );
        return promise;
    }

    /**
     * Returned {@link AsyncResult} instance isn't guarantied to be marked as rejected in all cases where importing wasn't performed (e.g.
     * if project is closed)
     */
    public AsyncResult<List<Module>> scheduleImportAndResolve() {
        // scheduleImport will be called after the scheduleResolve process has finished
        AsyncResult<List<Module>> promise = scheduleResolve();
        fireImportAndResolveScheduled();
        return promise;
    }

    private AsyncResult<List<Module>> scheduleResolve() {
        final AsyncResult<List<Module>> result = new AsyncResult<>();
        runWhenFullyOpen(() ->
        {
            LinkedHashSet<MavenProject> toResolve;
            synchronized (myImportingDataLock) {
                toResolve = new LinkedHashSet<>(myProjectsToResolve);
                myProjectsToResolve.clear();
            }
            final ResolveContext context = new ResolveContext();

            Iterator<MavenProject> it = toResolve.iterator();
            while (it.hasNext()) {
                MavenProject each = it.next();
                Runnable onCompletion = it.hasNext() ? null : () -> {
                    if (hasScheduledProjects()) {
                        scheduleImport().notify(result);
                    }
                    else {
                        result.setDone(Collections.<Module>emptyList());
                    }
                };

                myResolvingProcessor.scheduleTask(new MavenProjectsProcessorResolvingTask(
                    each,
                    myProjectsTree,
                    getGeneralSettings(),
                    onCompletion,
                    context
                ));
            }
        });
        return result;
    }

    public void evaluateEffectivePom(@Nonnull final MavenProject mavenProject, @Nonnull final Consumer<String> consumer) {
        runWhenFullyOpen(() -> myResolvingProcessor.scheduleTask((project, embeddersManager, console, indicator) -> {
            indicator.setText("Evaluating effective POM");

            myProjectsTree.executeWithEmbedder(
                mavenProject,
                getEmbeddersManager(),
                MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE,
                console,
                indicator,
                embedder ->
                {
                    try {
                        MavenExplicitProfiles profiles = mavenProject.getActivatedProfilesIds();
                        String res = embedder.evaluateEffectivePom(
                            mavenProject.getFile(),
                            profiles.getEnabledProfiles(),
                            profiles.getDisabledProfiles()
                        );
                        consumer.accept(res);
                    }
                    catch (UnsupportedOperationException e) {
                        consumer.accept(null); // null means UnsupportedOperationException
                    }
                }
            );
        }));
    }

    @TestOnly
    public void scheduleResolveInTests(Collection<MavenProject> projects) {
        scheduleForNextResolve(projects);
        scheduleResolve();
    }

    @TestOnly
    public void scheduleResolveAllInTests() {
        scheduleResolveInTests(getProjects());
    }

    public void scheduleFoldersResolve(final Collection<MavenProject> projects) {
        runWhenFullyOpen(() ->
        {
            Iterator<MavenProject> it = projects.iterator();
            while (it.hasNext()) {
                MavenProject each = it.next();
                Runnable onCompletion = it.hasNext() ? null : () -> {
                    if (hasScheduledProjects()) {
                        scheduleImport();
                    }
                };
                myFoldersResolvingProcessor.scheduleTask(new MavenProjectsProcessorFoldersResolvingTask(
                    each,
                    getImportingSettings(),
                    myProjectsTree,
                    onCompletion
                ));
            }
        });
    }

    public void scheduleFoldersResolveForAllProjects() {
        scheduleFoldersResolve(getProjects());
    }

    private void schedulePluginsResolve(final MavenProject project, final NativeMavenProjectHolder nativeMavenProject) {
        runWhenFullyOpen(() -> myPluginsResolvingProcessor.scheduleTask(new MavenProjectsProcessorPluginsResolvingTask(
            project,
            nativeMavenProject,
            myProjectsTree
        )));
    }

    public void scheduleArtifactsDownloading(
        final Collection<MavenProject> projects,
        @Nullable final Collection<MavenArtifact> artifacts,
        final boolean sources,
        final boolean docs,
        @Nullable final AsyncResult<MavenArtifactDownloader.DownloadResult> result
    ) {
        if (!sources && !docs) {
            return;
        }

        runWhenFullyOpen(() -> myArtifactsDownloadingProcessor.scheduleTask(new MavenProjectsProcessorArtifactsDownloadingTask(
            projects,
            artifacts,
            myProjectsTree,
            sources,
            docs,
            result
        )));
    }

    private void scheduleImportSettings() {
        scheduleImportSettings(false);
    }

    private void scheduleImportSettings(boolean importModuleGroupsRequired) {
        synchronized (myImportingDataLock) {
            myImportModuleGroupsRequired = importModuleGroupsRequired;
        }
        scheduleImport();
    }

    private AsyncResult<List<Module>> scheduleImport() {
        final AsyncResult<List<Module>> result = new AsyncResult<>();
        runWhenFullyOpen(() -> myImportingQueue.queue(new Update(MavenProjectsManager.this) {
            @Override
            public void run() {
                result.setDone(importProjects());
            }
        }));
        return result;
    }

    @TestOnly
    public void scheduleImportInTests(List<VirtualFile> projectFiles) {
        List<Pair<MavenProject, MavenProjectChanges>> toImport = new ArrayList<>();
        for (VirtualFile each : projectFiles) {
            MavenProject project = findProject(each);
            if (project != null) {
                toImport.add(Pair.create(project, MavenProjectChanges.ALL));
            }
        }
        scheduleForNextImport(toImport);
        scheduleImport();
    }

    private void scheduleForNextImport(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
        scheduleForNextImport(Collections.singletonList(projectWithChanges));
    }

    private void scheduleForNextImport(Collection<Pair<MavenProject, MavenProjectChanges>> projectsWithChanges) {
        synchronized (myImportingDataLock) {
            for (Pair<MavenProject, MavenProjectChanges> each : projectsWithChanges) {
                MavenProjectChanges changes = each.second.mergedWith(myProjectsToImport.get(each.first));
                myProjectsToImport.put(each.first, changes);
            }
        }
    }

    private void scheduleForNextResolve(Collection<MavenProject> projects) {
        synchronized (myImportingDataLock) {
            myProjectsToResolve.addAll(projects);
        }
    }

    public boolean hasScheduledProjects() {
        if (!isInitialized()) {
            return false;
        }
        synchronized (myImportingDataLock) {
            return !myProjectsToImport.isEmpty() || !myProjectsToResolve.isEmpty();
        }
    }

    @TestOnly
    public boolean hasScheduledImportsInTests() {
        return isInitialized() && !myImportingQueue.isEmpty();
    }

    @TestOnly
    public void performScheduledImportInTests() {
        if (!isInitialized()) {
            return;
        }
        runWhenFullyOpen(() -> myImportingQueue.flush());
    }

    private void runWhenFullyOpen(final Runnable runnable) {
        if (!isInitialized()) {
            return; // may be called from scheduleImport after project started closing and before it is closed.
        }

        if (isNoBackgroundMode()) {
            runnable.run();
            return;
        }

        final Ref<Runnable> wrapper = new Ref<>();
        wrapper.set(() ->
        {
            if (!StartupManager.getInstance(myProject).postStartupActivityPassed()) {
                // should not remove previously schedules tasks
                myInitializationAlarm.addRequest(() -> wrapper.get().run(), 1000);
                return;
            }
            runnable.run();
        });
        MavenUtil.runWhenInitialized(myProject, wrapper.get());
    }

    private void schedulePostImportTasks(List<MavenProjectsProcessorTask> postTasks) {
        for (MavenProjectsProcessorTask each : postTasks) {
            myPostProcessor.scheduleTask(each);
        }
    }

    private void unscheduleAllTasks(List<MavenProject> projects) {
        for (MavenProject each : projects) {
            MavenProjectsProcessorEmptyTask dummyTask = new MavenProjectsProcessorEmptyTask(each);

            synchronized (myImportingDataLock) {
                myProjectsToImport.remove(each);
                myProjectsToResolve.remove(each);
            }

            myResolvingProcessor.removeTask(dummyTask);
            myPluginsResolvingProcessor.removeTask(dummyTask);
            myFoldersResolvingProcessor.removeTask(dummyTask);
            myPostProcessor.removeTask(dummyTask);
        }
    }

    @TestOnly
    public void unscheduleAllTasksInTests() {
        unscheduleAllTasks(getProjects());
    }

    public void waitForReadingCompletion() {
        waitForTasksCompletion(null);
    }

    public void waitForResolvingCompletion() {
        waitForTasksCompletion(myResolvingProcessor);
    }

    public void waitForFoldersResolvingCompletion() {
        waitForTasksCompletion(myFoldersResolvingProcessor);
    }

    public void waitForPluginsResolvingCompletion() {
        waitForTasksCompletion(myPluginsResolvingProcessor);
    }

    public void waitForArtifactsDownloadingCompletion() {
        waitForTasksCompletion(myArtifactsDownloadingProcessor);
    }

    public void waitForPostImportTasksCompletion() {
        myPostProcessor.waitForCompletion();
    }

    private void waitForTasksCompletion(MavenProjectsProcessor processor) {
        FileDocumentManager.getInstance().saveAllDocuments();

        myReadingProcessor.waitForCompletion();
        if (processor != null) {
            processor.waitForCompletion();
        }
    }

    public void updateProjectTargetFolders() {
        Application.get().invokeLater(() -> {
            if (myProject.isDisposed()) {
                return;
            }

            MavenFoldersImporter.updateProjectFolders(myProject, true);
            VirtualFileManager.getInstance().asyncRefresh(null);
        });
    }

    public List<Module> importProjects() {
        return importProjects(new MavenDefaultModifiableModelsProvider(myProject));
    }

    public List<consulo.module.Module> importProjects(final MavenModifiableModelsProvider modelsProvider) {
        final Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges;
        final boolean importModuleGroupsRequired;
        synchronized (myImportingDataLock) {
            projectsToImportWithChanges = new LinkedHashMap<>(myProjectsToImport);
            myProjectsToImport.clear();
            importModuleGroupsRequired = myImportModuleGroupsRequired;
            myImportModuleGroupsRequired = false;
        }

        final Ref<MavenProjectImporter> importer = new Ref<>();
        final Ref<List<MavenProjectsProcessorTask>> postTasks = new Ref<>();

        final Runnable r = () ->
        {
            MavenProjectImporter projectImporter =
                new MavenProjectImporter(myProject, myProjectsTree, getFileToModuleMapping(modelsProvider), projectsToImportWithChanges,
                    importModuleGroupsRequired, modelsProvider, getImportingSettings()
                );
            importer.set(projectImporter);
            postTasks.set(projectImporter.importProject());
        };

        // called from wizard or ui
        if (Application.get().isDispatchThread()) {
            r.run();
        }
        else {
            MavenUtil.runInBackground(
                myProject,
                MavenProjectLocalize.mavenProjectImporting().get(),
                false,
                indicator -> r.run()
            ).waitFor();
        }


        VirtualFileManager fm = VirtualFileManager.getInstance();
        if (isNormalProject()) {
            fm.asyncRefresh(null);
        }
        else {
            fm.syncRefresh();
        }

        if (postTasks.get() != null /*may be null if importing is cancelled*/) {
            schedulePostImportTasks(postTasks.get());
        }

        // do not block user too often
        myImportingQueue.restartTimer();

        MavenProjectImporter projectImporter = importer.get();
        if (projectImporter == null) {
            return Collections.emptyList();
        }

        return projectImporter.getCreatedModules();
    }

    private static Map<VirtualFile, Module> getFileToModuleMapping(MavenModelsProvider modelsProvider) {
        Map<VirtualFile, Module> result = new HashMap<>();
        for (Module each : modelsProvider.getModules()) {
            VirtualFile f = findPomFile(each, modelsProvider);
            if (f != null) {
                result.put(f, each);
            }
        }
        return result;
    }

    private List<VirtualFile> collectAllAvailablePomFiles() {
        List<VirtualFile> result = new ArrayList<>(getFileToModuleMapping(new MavenDefaultModelsProvider(myProject)).keySet());

        VirtualFile pom = myProject.getBaseDir().findChild(MavenConstants.POM_XML);
        if (pom != null) {
            result.add(pom);
        }

        return result;
    }

    public void addManagerListener(Listener listener) {
        myManagerListeners.add(listener);
    }

    public void addProjectsTreeListener(MavenProjectsTree.Listener listener) {
        myProjectsTreeDispatcher.addListener(listener);
    }

    @TestOnly
    public void fireActivatedInTests() {
        fireActivated();
    }

    private void fireActivated() {
        for (Listener each : myManagerListeners) {
            each.activated();
        }
    }

    private void fireProjectScheduled() {
        for (Listener each : myManagerListeners) {
            each.projectsScheduled();
        }
    }

    private void fireImportAndResolveScheduled() {
        for (Listener each : myManagerListeners) {
            each.importAndResolveScheduled();
        }
    }

    public interface Listener {
        default void activated() {
        }

        default void projectsScheduled() {
        }

        default void importAndResolveScheduled() {
        }
    }
}
