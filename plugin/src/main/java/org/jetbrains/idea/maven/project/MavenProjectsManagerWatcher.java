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

import consulo.application.WriteAction;
import consulo.codeEditor.EditorFactory;
import consulo.component.messagebus.MessageBusConnection;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.ide.impl.idea.openapi.fileEditor.impl.FileDocumentManagerImpl;
import consulo.language.psi.PsiDocumentManager;
import consulo.maven.rt.server.common.model.MavenConstants;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.module.Module;
import consulo.module.content.layer.event.ModuleRootAdapter;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.module.event.ModuleAdapter;
import consulo.module.event.ModuleListener;
import consulo.project.Project;
import consulo.ui.ex.awt.util.Update;
import consulo.util.collection.ContainerUtil;
import consulo.util.concurrent.AsyncResult;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.io.PathUtil;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.NewVirtualFile;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.*;
import consulo.virtualFileSystem.pointer.VirtualFilePointer;
import consulo.virtualFileSystem.pointer.VirtualFilePointerListener;
import consulo.virtualFileSystem.pointer.VirtualFilePointerManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.util.VirtualFileVisitor;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.utils.MavenMergingUpdateQueue;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class MavenProjectsManagerWatcher {
    private static final Key<ConcurrentMap<Project, Integer>> CRC_WITHOUT_SPACES =
        Key.create("MavenProjectsManagerWatcher.CRC_WITHOUT_SPACES");

    public static final Key<Boolean> FORCE_IMPORT_AND_RESOLVE_ON_REFRESH =
        Key.create(MavenProjectsManagerWatcher.class + "FORCE_IMPORT_AND_RESOLVE_ON_REFRESH");

    private static final int DOCUMENT_SAVE_DELAY = 1000;

    private final Project myProject;
    private final MavenProjectsManager myManager;
    private final MavenProjectsTree myProjectsTree;
    private final MavenGeneralSettings myGeneralSettings;
    private final MavenProjectsProcessor myReadingProcessor;
    private final MavenEmbeddersManager myEmbeddersManager;

    private final List<VirtualFilePointer> mySettingsFilesPointers = new ArrayList<>();
    private final List<LocalFileSystem.WatchRequest> myWatchedRoots = new ArrayList<>();

    private final Set<Document> myChangedDocuments = new HashSet<>();
    private final MavenMergingUpdateQueue myChangedDocumentsQueue;

    public MavenProjectsManagerWatcher(
        Project project,
        MavenProjectsManager manager,
        MavenProjectsTree projectsTree,
        MavenGeneralSettings generalSettings,
        MavenProjectsProcessor readingProcessor,
        MavenEmbeddersManager embeddersManager
    ) {
        myProject = project;
        myManager = manager;
        myProjectsTree = projectsTree;
        myGeneralSettings = generalSettings;
        myReadingProcessor = readingProcessor;
        myEmbeddersManager = embeddersManager;

        myChangedDocumentsQueue =
            new MavenMergingUpdateQueue(getClass() + ": Document changes queue", DOCUMENT_SAVE_DELAY, false, myProject);
    }

    public synchronized void start() {
        final MessageBusConnection connection = myProject.getMessageBus().connect(myChangedDocumentsQueue);
        connection.subscribe(BulkFileListener.class, new MyFileChangeListener());
        connection.subscribe(ModuleRootListener.class, new MyRootChangesListener());

        myChangedDocumentsQueue.makeUserAware(myProject);
        myChangedDocumentsQueue.activate();

        connection.subscribe(ModuleListener.class, new ModuleAdapter() {
            @Override
            public void moduleRemoved(@Nonnull Project project, @Nonnull consulo.module.Module module) {
                MavenProject mavenProject = myManager.findProject(module);
                if (mavenProject != null && !myManager.isIgnored(mavenProject)) {
                    VirtualFile file = mavenProject.getFile();

                    if (myManager.isManagedFile(file) && myManager.getModules(mavenProject).isEmpty()) {
                        myManager.removeManagedFiles(Collections.singletonList(file));
                    }
                    else {
                        myManager.setIgnoredState(Collections.singletonList(mavenProject), true);
                    }
                }
            }

            @Override
            public void moduleAdded(@Nonnull final Project project, @Nonnull final Module module) {
                // this method is needed to return non-ignored status for modules that were deleted (and thus ignored)
                // and then created again with a different module type
                if (myManager.isMavenizedModule(module)) {
                    MavenProject mavenProject = myManager.findProject(module);
                    if (mavenProject != null) {
                        myManager.setIgnoredState(Collections.singletonList(mavenProject), false);
                    }
                }
            }
        });

        DocumentAdapter myDocumentListener = new DocumentAdapter() {
            @Override
            public void documentChanged(DocumentEvent event) {
                Document doc = event.getDocument();
                VirtualFile file = FileDocumentManager.getInstance().getFile(doc);

                if (file == null) {
                    return;
                }
                boolean isMavenFile = file.getName().equals(MavenConstants.POM_XML)
                    || file.getName().equals(MavenConstants.PROFILES_XML)
                    || isSettingsFile(file);
                if (!isMavenFile) {
                    return;
                }

                synchronized (myChangedDocuments) {
                    myChangedDocuments.add(doc);
                }
                myChangedDocumentsQueue.queue(new Update(MavenProjectsManagerWatcher.this) {
                    @Override
                    public void run() {
                        final Document[] copy;

                        synchronized (myChangedDocuments) {
                            copy = myChangedDocuments.toArray(new Document[myChangedDocuments.size()]);
                            myChangedDocuments.clear();
                        }

                        MavenUtil.invokeLater(
                            myProject,
                            () -> WriteAction.run(() -> {
                                for (Document each : copy) {
                                    PsiDocumentManager.getInstance(myProject).commitDocument(each);
                                    ((FileDocumentManagerImpl)FileDocumentManager.getInstance()).saveDocument(each, false);
                                }
                            })
                        );
                    }
                });
            }
        };
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myDocumentListener, myChangedDocumentsQueue);

        final MavenGeneralSettings.Listener mySettingsPathsChangesListener = () -> {
            updateSettingsFilePointers();
            onSettingsChange();
        };
        myGeneralSettings.addListener(mySettingsPathsChangesListener);
        Disposer.register(
            myChangedDocumentsQueue,
            () -> {
                myGeneralSettings.removeListener(mySettingsPathsChangesListener);
                mySettingsFilesPointers.clear();
            }
        );
        updateSettingsFilePointers();
    }

    private void updateSettingsFilePointers() {
        LocalFileSystem.getInstance().removeWatchedRoots(myWatchedRoots);
        mySettingsFilesPointers.clear();
        addFilePointer(myGeneralSettings.getEffectiveUserSettingsIoFile(), myGeneralSettings.getEffectiveGlobalSettingsIoFile());
    }

    private void addFilePointer(File... settingsFiles) {
        Collection<String> pathsToWatch = new ArrayList<>(settingsFiles.length);

        for (File settingsFile : settingsFiles) {
            if (settingsFile == null) {
                continue;
            }

            File parentFile = settingsFile.getParentFile();
            if (parentFile != null) {
                String path = getNormalizedPath(parentFile);
                if (path != null) {
                    pathsToWatch.add(path);
                }
            }

            String path = getNormalizedPath(settingsFile);
            if (path != null) {
                String url = VirtualFileUtil.pathToUrl(path);
                mySettingsFilesPointers.add(VirtualFilePointerManager.getInstance().create(
                    url,
                    myChangedDocumentsQueue,
                    new VirtualFilePointerListener() {
                        @Override
                        public void beforeValidityChanged(@Nonnull VirtualFilePointer[] pointers) {
                        }

                        @Override
                        public void validityChanged(@Nonnull VirtualFilePointer[] pointers) {
                        }
                    }
                ));
            }
        }

        myWatchedRoots.addAll(LocalFileSystem.getInstance().addRootsToWatch(pathsToWatch, false));
    }

    @Nullable
    private static String getNormalizedPath(@Nonnull File settingsFile) {
        String canonized = PathUtil.getCanonicalPath(settingsFile.getAbsolutePath());
        return canonized == null ? null : FileUtil.toSystemIndependentName(canonized);
    }

    public synchronized void stop() {
        Disposer.dispose(myChangedDocumentsQueue);
    }

    public synchronized void addManagedFilesWithProfiles(List<VirtualFile> files, MavenExplicitProfiles explicitProfiles) {
        myProjectsTree.addManagedFilesWithProfiles(files, explicitProfiles);
        scheduleUpdateAll(false, true);
    }

    @TestOnly
    public synchronized void resetManagedFilesAndProfilesInTests(List<VirtualFile> files, MavenExplicitProfiles explicitProfiles) {
        myProjectsTree.resetManagedFilesAndProfiles(files, explicitProfiles);
        scheduleUpdateAll(false, true);
    }

    public synchronized void removeManagedFiles(List<VirtualFile> files) {
        myProjectsTree.removeManagedFiles(files);
        scheduleUpdateAll(false, true);
    }

    public synchronized void setExplicitProfiles(MavenExplicitProfiles profiles) {
        myProjectsTree.setExplicitProfiles(profiles);
        scheduleUpdateAll(false, false);
    }

    /**
     * Returned {@link AsyncResult} instance isn't guarantied to be marked as rejected in all cases where importing wasn't performed (e.g.
     * if project is closed)
     */
    public AsyncResult<Void> scheduleUpdateAll(boolean force, final boolean forceImportAndResolve) {
        final AsyncResult<Void> promise = new AsyncResult<>();
        Runnable onCompletion = createScheduleImportAction(forceImportAndResolve, promise);
        myReadingProcessor.scheduleTask(new MavenProjectsProcessorReadingTask(force, myProjectsTree, myGeneralSettings, onCompletion));
        return promise;
    }

    public AsyncResult<Void> scheduleUpdate(
        List<VirtualFile> filesToUpdate,
        List<VirtualFile> filesToDelete,
        boolean force,
        final boolean forceImportAndResolve
    ) {
        final AsyncResult<Void> promise = new AsyncResult<>();
        Runnable onCompletion = createScheduleImportAction(forceImportAndResolve, promise);
        myReadingProcessor.scheduleTask(new MavenProjectsProcessorReadingTask(
            filesToUpdate,
            filesToDelete,
            force,
            myProjectsTree,
            myGeneralSettings,
            onCompletion
        ));
        return promise;
    }

    @Nonnull
    private Runnable createScheduleImportAction(final boolean forceImportAndResolve, final AsyncResult<Void> promise) {
        return () -> {
            if (myProject.isDisposed()) {
                promise.reject("Project disposed");
                return;
            }

            if (forceImportAndResolve || myManager.getImportingSettings().isImportAutomatically()) {
                myManager.scheduleImportAndResolve().doWhenDone(modules -> promise.setDone(null));
            }
            else {
                promise.setDone(null);
            }
        };
    }

    private void onSettingsChange() {
        myEmbeddersManager.reset();
        scheduleUpdateAll(true, false);
    }

    private void onSettingsXmlChange() {
        myGeneralSettings.changed();
        // onSettingsChange() will be called indirectly by pathsChanged listener on GeneralSettings object
    }

    private class MyRootChangesListener extends ModuleRootAdapter {
        @Override
        public void rootsChanged(ModuleRootEvent event) {
            // todo is this logic necessary?
            List<VirtualFile> existingFiles = myProjectsTree.getProjectsFiles();
            List<VirtualFile> newFiles = new ArrayList<>();
            List<VirtualFile> deletedFiles = new ArrayList<>();

            for (VirtualFile f : myProjectsTree.getExistingManagedFiles()) {
                if (!existingFiles.contains(f)) {
                    newFiles.add(f);
                }
            }

            for (VirtualFile f : existingFiles) {
                if (!f.isValid()) {
                    deletedFiles.add(f);
                }
            }

            scheduleUpdate(newFiles, deletedFiles, false, false);
        }
    }

    private boolean isPomFile(String path) {
        return path.endsWith("/" + MavenConstants.POM_XML) && myProjectsTree.isPotentialProject(path);
    }

    private boolean isProfilesFile(String path) {
        return path.endsWith("/" + MavenConstants.PROFILES_XML)
            && myProjectsTree.isPotentialProject(
                path.substring(0, path.length() - MavenConstants.PROFILES_XML.length()) + MavenConstants.POM_XML
            );
    }

    private boolean isSettingsFile(String path) {
        for (VirtualFilePointer each : mySettingsFilesPointers) {
            VirtualFile f = each.getFile();
            if (f != null && FileUtil.pathsEqual(path, f.getPath())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSettingsFile(VirtualFile f) {
        for (VirtualFilePointer each : mySettingsFilesPointers) {
            if (Comparing.equal(each.getFile(), f)) {
                return true;
            }
        }
        return false;
    }

    private class MyFileChangeListener extends MyFileChangeListenerBase {
        private List<VirtualFile> filesToUpdate;
        private List<VirtualFile> filesToRemove;
        private boolean settingsHaveChanged;
        private boolean forceImportAndResolve;

        @Override
        protected boolean isRelevant(String path) {
            return isPomFile(path) || isProfilesFile(path) || isSettingsFile(path);
        }

        @Override
        protected void updateFile(VirtualFile file, VFileEvent event) {
            doUpdateFile(file, event, false);
        }

        @Override
        protected void deleteFile(VirtualFile file, VFileEvent event) {
            doUpdateFile(file, event, true);
        }

        private void doUpdateFile(VirtualFile file, VFileEvent event, boolean remove) {
            initLists();

            if (isSettingsFile(file)) {
                settingsHaveChanged = true;
                return;
            }

            if (file.getUserData(FORCE_IMPORT_AND_RESOLVE_ON_REFRESH) == Boolean.TRUE) {
                forceImportAndResolve = true;
            }

            VirtualFile pom = getPomFileProfilesFile(file);
            if (pom != null) {
                if (remove || xmlFileWasChanged(pom, event)) {
                    filesToUpdate.add(pom);
                }
                return;
            }

            if (remove) {
                filesToRemove.add(file);
            }
            else if (xmlFileWasChanged(file, event)) {
                filesToUpdate.add(file);
            }
        }

        private boolean xmlFileWasChanged(VirtualFile xmlFile, VFileEvent event) {
            if (!xmlFile.isValid() || !(event instanceof VFileContentChangeEvent)) {
                return true;
            }

            ConcurrentMap<Project, Integer> map = xmlFile.getUserData(CRC_WITHOUT_SPACES);
            if (map == null) {
                ConcurrentMap<Project, Integer> value = ContainerUtil.createConcurrentWeakMap();
                map = xmlFile.putUserDataIfAbsent(CRC_WITHOUT_SPACES, value);
            }

            Integer crc = map.get(myProject);
            Integer newCrc;

            try {
                newCrc = MavenUtil.crcWithoutSpaces(xmlFile);
            }
            catch (IOException ignored) {
                return true;
            }

            if (newCrc == -1 /* XML is invalid */ || newCrc.equals(crc)) {
                return false;
            }
            else {
                map.put(myProject, newCrc);
                return true;
            }
        }

        @Nullable
        private VirtualFile getPomFileProfilesFile(VirtualFile f) {
            if (!f.getName().equals(MavenConstants.PROFILES_XML)) {
                return null;
            }
            return f.getParent().findChild(MavenConstants.POM_XML);
        }

        @Override
        protected void apply() {
            // the save may occur during project close. in this case the background task
            // can not be started since the window has already been closed.
            if (areFileSetsInitialised()) {
                if (settingsHaveChanged) {
                    onSettingsXmlChange();
                }
                else {
                    filesToUpdate.removeAll(filesToRemove);
                    scheduleUpdate(filesToUpdate, filesToRemove, false, forceImportAndResolve);
                }
            }

            clearLists();
        }

        private boolean areFileSetsInitialised() {
            return filesToUpdate != null;
        }

        private void initLists() {
            // Do not use before() method to initialize the lists
            // since the listener can be attached during the update
            // and before method can be skipped.
            // The better way to fix if, of course, is to do something with
            // subscription - add listener not during postStartupActivity
            // but on project initialization to avoid this situation.
            if (areFileSetsInitialised()) {
                return;
            }

            filesToUpdate = new ArrayList<>();
            filesToRemove = new ArrayList<>();
            settingsHaveChanged = false;
            forceImportAndResolve = false;
        }

        private void clearLists() {
            filesToUpdate = null;
            filesToRemove = null;
        }
    }

    private static abstract class MyFileChangeListenerBase implements BulkFileListener {
        protected abstract boolean isRelevant(String path);

        protected abstract void updateFile(VirtualFile file, VFileEvent event);

        protected abstract void deleteFile(VirtualFile file, VFileEvent event);

        protected abstract void apply();

        @Override
        public void before(@Nonnull List<? extends VFileEvent> events) {
            for (VFileEvent each : events) {
                if (each instanceof VFileDeleteEvent) {
                    deleteRecursively(each.getFile(), each);
                }
                else {
                    if (!isRelevant(each.getPath())) {
                        continue;
                    }
                    if (each instanceof VFilePropertyChangeEvent) {
                        if (isRenamed(each)) {
                            deleteRecursively(each.getFile(), each);
                        }
                    }
                    else if (each instanceof VFileMoveEvent moveEvent) {
                        String newPath = moveEvent.getNewParent().getPath() + "/" + moveEvent.getFile().getName();
                        if (!isRelevant(newPath)) {
                            deleteRecursively(moveEvent.getFile(), each);
                        }
                    }
                }
            }
        }

        private void deleteRecursively(VirtualFile f, final VFileEvent event) {
            VirtualFileUtil.visitChildrenRecursively(f, new VirtualFileVisitor() {
                @Override
                public boolean visitFile(@Nonnull VirtualFile f) {
                    if (isRelevant(f.getPath())) {
                        deleteFile(f, event);
                    }
                    return true;
                }

                @Nullable
                @Override
                public Iterable<VirtualFile> getChildrenIterable(@Nonnull VirtualFile f) {
                    return f.isDirectory() && f instanceof NewVirtualFile newVirtualFile ? newVirtualFile.iterInDbChildren() : null;
                }
            });
        }

        @Override
        public void after(@Nonnull List<? extends VFileEvent> events) {
            for (VFileEvent each : events) {
                if (!isRelevant(each.getPath())) {
                    continue;
                }

                if (each instanceof VFileCreateEvent createEvent) {
                    VirtualFile newChild = createEvent.getParent().findChild(createEvent.getChildName());
                    if (newChild != null) {
                        updateFile(newChild, each);
                    }
                }
                else if (each instanceof VFileCopyEvent copyEvent) {
                    VirtualFile newChild = copyEvent.getNewParent().findChild(copyEvent.getNewChildName());
                    if (newChild != null) {
                        updateFile(newChild, each);
                    }
                }
                else if (each instanceof VFileContentChangeEvent) {
                    updateFile(each.getFile(), each);
                }
                else if (each instanceof VFilePropertyChangeEvent) {
                    if (isRenamed(each)) {
                        updateFile(each.getFile(), each);
                    }
                }
                else if (each instanceof VFileMoveEvent) {
                    updateFile(each.getFile(), each);
                }
            }
            apply();
        }

        private static boolean isRenamed(VFileEvent each) {
            return ((VFilePropertyChangeEvent)each).getPropertyName().equals(VirtualFile.PROP_NAME)
                && !Comparing.equal(((VFilePropertyChangeEvent)each).getOldValue(), ((VFilePropertyChangeEvent)each).getNewValue());
        }
    }
}
