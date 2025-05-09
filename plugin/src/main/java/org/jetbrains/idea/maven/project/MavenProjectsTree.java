/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.logging.Logger;
import consulo.maven.rt.server.common.model.*;
import consulo.maven.rt.server.common.server.NativeMavenProjectHolder;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.Lists;
import consulo.util.collection.Sets;
import consulo.util.collection.Stack;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.dom.references.MavenFilteredPropertyPsiReferenceProvider;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.*;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public class MavenProjectsTree {
    private static final Logger LOG = Logger.getInstance(MavenProjectsTree.class);

    private static final String STORAGE_VERSION = MavenProjectsTree.class.getSimpleName() + ".6";

    private final Object myStateLock = new Object();
    private final ReentrantReadWriteLock myStructureLock = new ReentrantReadWriteLock();
    private final Lock myStructureReadLock = myStructureLock.readLock();
    private final Lock myStructureWriteLock = myStructureLock.writeLock();

    // TODO replace with sets
    private volatile Set<String> myManagedFilesPaths = new LinkedHashSet<>();
    private volatile List<String> myIgnoredFilesPaths = new ArrayList<>();
    private volatile List<String> myIgnoredFilesPatterns = new ArrayList<>();
    private volatile Pattern myIgnoredFilesPatternsCache;

    private MavenExplicitProfiles myExplicitProfiles = MavenExplicitProfiles.NONE;
    private final MavenExplicitProfiles myTemporarilyRemovedExplicitProfiles =
        new MavenExplicitProfiles(new HashSet<>(), new HashSet<>());

    private final List<MavenProject> myRootProjects = new ArrayList<>();

    private final Map<MavenProject, MavenProjectTimestamp> myTimestamps = new HashMap<>();
    private final MavenWorkspaceMap myWorkspaceMap = new MavenWorkspaceMap();
    private final Map<MavenId, MavenProject> myMavenIdToProjectMapping = new HashMap<>();
    private final Map<VirtualFile, MavenProject> myVirtualFileToProjectMapping = new HashMap<>();
    private final Map<MavenProject, List<MavenProject>> myAggregatorToModuleMapping = new HashMap<>();
    private final Map<MavenProject, MavenProject> myModuleToAggregatorMapping = new HashMap<>();

    private final List<Listener> myListeners = Lists.newLockFreeCopyOnWriteList();

    private final MavenProjectReaderProjectLocator myProjectLocator = coordinates -> {
        MavenProject project = findProject(coordinates);
        return project == null ? null : project.getFile();
    };

    @Nullable
    public static MavenProjectsTree read(File file) throws IOException {
        MavenProjectsTree result = new MavenProjectsTree();

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            try {
                if (!STORAGE_VERSION.equals(in.readUTF())) {
                    return null;
                }
                result.myManagedFilesPaths = readCollection(in, new LinkedHashSet<>());
                result.myIgnoredFilesPaths = readCollection(in, new ArrayList<>());
                result.myIgnoredFilesPatterns = readCollection(in, new ArrayList<>());
                result.myExplicitProfiles =
                    new MavenExplicitProfiles(readCollection(in, new HashSet<>()), readCollection(in, new HashSet<>()));
                result.myRootProjects.addAll(readProjectsRecursively(in, result));
            }
            catch (IOException e) {
                in.close();
                file.delete();
                throw e;
            }
            catch (Throwable e) {
                throw new IOException(e);
            }
        }
        return result;
    }

    private static <T extends Collection<String>> T readCollection(DataInputStream in, T result) throws IOException {
        int count = in.readInt();
        while (count-- > 0) {
            result.add(in.readUTF());
        }
        return result;
    }

    private static void writeCollection(DataOutputStream out, Collection<String> list) throws IOException {
        out.writeInt(list.size());
        for (String each : list) {
            out.writeUTF(each);
        }
    }

    private static List<MavenProject> readProjectsRecursively(DataInputStream in, MavenProjectsTree tree) throws IOException {
        int count = in.readInt();
        List<MavenProject> result = new ArrayList<>(count);
        while (count-- > 0) {
            MavenProject project = MavenProject.read(in);
            MavenProjectTimestamp timestamp = MavenProjectTimestamp.read(in);
            List<MavenProject> modules = readProjectsRecursively(in, tree);
            if (project != null) {
                result.add(project);
                tree.myTimestamps.put(project, timestamp);
                tree.myVirtualFileToProjectMapping.put(project.getFile(), project);
                tree.fillIDMaps(project);
                tree.myAggregatorToModuleMapping.put(project, modules);
                for (MavenProject eachModule : modules) {
                    tree.myModuleToAggregatorMapping.put(eachModule, project);
                }
            }
        }
        return result;
    }

    public void save(File file) throws IOException {
        synchronized (myStateLock) {
            readLock();
            try {
                file.getParentFile().mkdirs();
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
                    out.writeUTF(STORAGE_VERSION);
                    writeCollection(out, myManagedFilesPaths);
                    writeCollection(out, myIgnoredFilesPaths);
                    writeCollection(out, myIgnoredFilesPatterns);
                    writeCollection(out, myExplicitProfiles.getEnabledProfiles());
                    writeCollection(out, myExplicitProfiles.getDisabledProfiles());
                    writeProjectsRecursively(out, myRootProjects);
                }
            }
            finally {
                readUnlock();
            }
        }
    }

    private void writeProjectsRecursively(DataOutputStream out, List<MavenProject> list) throws IOException {
        out.writeInt(list.size());
        for (MavenProject each : list) {
            each.write(out);
            myTimestamps.get(each).write(out);
            writeProjectsRecursively(out, getModules(each));
        }
    }

    public List<String> getManagedFilesPaths() {
        synchronized (myStateLock) {
            return new ArrayList<>(myManagedFilesPaths);
        }
    }

    public void resetManagedFilesPathsAndProfiles(List<String> paths, MavenExplicitProfiles profiles) {
        synchronized (myStateLock) {
            myManagedFilesPaths = new LinkedHashSet<>(paths);
        }
        setExplicitProfiles(profiles);
    }

    @TestOnly
    public void resetManagedFilesAndProfiles(List<VirtualFile> files, MavenExplicitProfiles profiles) {
        resetManagedFilesPathsAndProfiles(MavenUtil.collectPaths(files), profiles);
    }

    public void addManagedFilesWithProfiles(List<VirtualFile> files, MavenExplicitProfiles profiles) {
        List<String> newFiles;
        MavenExplicitProfiles newProfiles;
        synchronized (myStateLock) {
            newFiles = new ArrayList<>(myManagedFilesPaths);
            newFiles.addAll(MavenUtil.collectPaths(files));

            newProfiles = myExplicitProfiles.clone();
            newProfiles.getEnabledProfiles().addAll(profiles.getEnabledProfiles());
            newProfiles.getDisabledProfiles().addAll(profiles.getDisabledProfiles());
        }

        resetManagedFilesPathsAndProfiles(newFiles, newProfiles);
    }

    public void removeManagedFiles(List<VirtualFile> files) {
        synchronized (myStateLock) {
            myManagedFilesPaths.removeAll(MavenUtil.collectPaths(files));
        }
    }

    public List<VirtualFile> getExistingManagedFiles() {
        List<VirtualFile> result = new ArrayList<>();
        for (String path : getManagedFilesPaths()) {
            VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
            if (f != null) {
                result.add(f);
            }
        }
        return result;
    }

    public List<String> getIgnoredFilesPaths() {
        synchronized (myStateLock) {
            return new ArrayList<>(myIgnoredFilesPaths);
        }
    }

    public void setIgnoredFilesPaths(final List<String> paths) {
        doChangeIgnoreStatus(() -> myIgnoredFilesPaths = new ArrayList<>(paths));
    }

    public void removeIgnoredFilesPaths(final Collection<String> paths) {
        doChangeIgnoreStatus(() -> myIgnoredFilesPaths.removeAll(paths));
    }

    public boolean getIgnoredState(MavenProject project) {
        synchronized (myStateLock) {
            return myIgnoredFilesPaths.contains(project.getPath());
        }
    }

    public void setIgnoredState(List<MavenProject> projects, boolean ignored) {
        setIgnoredState(projects, ignored, false);
    }

    public void setIgnoredState(List<MavenProject> projects, boolean ignored, boolean fromImport) {
        doSetIgnoredState(projects, ignored, fromImport);
    }

    private void doSetIgnoredState(List<MavenProject> projects, final boolean ignored, boolean fromImport) {
        final List<String> paths = MavenUtil.collectPaths(MavenUtil.collectFiles(projects));
        doChangeIgnoreStatus(
            () -> {
                if (ignored) {
                    myIgnoredFilesPaths.addAll(paths);
                }
                else {
                    myIgnoredFilesPaths.removeAll(paths);
                }
            },
            fromImport
        );
    }

    public List<String> getIgnoredFilesPatterns() {
        synchronized (myStateLock) {
            return new ArrayList<>(myIgnoredFilesPatterns);
        }
    }

    public void setIgnoredFilesPatterns(final List<String> patterns) {
        doChangeIgnoreStatus(() -> {
            myIgnoredFilesPatternsCache = null;
            myIgnoredFilesPatterns = new ArrayList<>(patterns);
        });
    }

    private void doChangeIgnoreStatus(Runnable runnable) {
        doChangeIgnoreStatus(runnable, false);
    }

    private void doChangeIgnoreStatus(Runnable runnable, boolean fromImport) {
        List<MavenProject> ignoredBefore;
        List<MavenProject> ignoredAfter;

        synchronized (myStateLock) {
            ignoredBefore = getIgnoredProjects();
            runnable.run();
            ignoredAfter = getIgnoredProjects();
        }

        List<MavenProject> ignored = new ArrayList<>(ignoredAfter);
        ignored.removeAll(ignoredBefore);

        List<MavenProject> unignored = new ArrayList<>(ignoredBefore);
        unignored.removeAll(ignoredAfter);

        if (ignored.isEmpty() && unignored.isEmpty()) {
            return;
        }

        fireProjectsIgnoredStateChanged(ignored, unignored, fromImport);
    }

    private List<MavenProject> getIgnoredProjects() {
        List<MavenProject> result = new ArrayList<>();
        for (MavenProject each : getProjects()) {
            if (isIgnored(each)) {
                result.add(each);
            }
        }
        return result;
    }

    public boolean isIgnored(MavenProject project) {
        String path = project.getPath();
        synchronized (myStateLock) {
            return myIgnoredFilesPaths.contains(path) || matchesIgnoredFilesPatterns(path);
        }
    }

    private boolean matchesIgnoredFilesPatterns(String path) {
        synchronized (myStateLock) {
            if (myIgnoredFilesPatternsCache == null) {
                myIgnoredFilesPatternsCache = Pattern.compile(Strings.translateMasks(myIgnoredFilesPatterns));
            }
            return myIgnoredFilesPatternsCache.matcher(path).matches();
        }
    }

    public MavenExplicitProfiles getExplicitProfiles() {
        synchronized (myStateLock) {
            return myExplicitProfiles.clone();
        }
    }

    public void setExplicitProfiles(MavenExplicitProfiles explicitProfiles) {
        synchronized (myStateLock) {
            myExplicitProfiles = explicitProfiles.clone();
        }
        fireProfilesChanged();
    }

    private void updateExplicitProfiles() {
        Collection<String> available = getAvailableProfiles();

        synchronized (myStateLock) {
            updateExplicitProfiles(
                myExplicitProfiles.getEnabledProfiles(),
                myTemporarilyRemovedExplicitProfiles.getEnabledProfiles(),
                available
            );
            updateExplicitProfiles(
                myExplicitProfiles.getDisabledProfiles(),
                myTemporarilyRemovedExplicitProfiles.getDisabledProfiles(),
                available
            );
        }
    }

    private void updateExplicitProfiles(
        Collection<String> explicitProfiles,
        Collection<String> temporarilyRemovedExplicitProfiles,
        Collection<String> available
    ) {
        Collection<String> removedProfiles = new HashSet<>(explicitProfiles);
        removedProfiles.removeAll(available);
        temporarilyRemovedExplicitProfiles.addAll(removedProfiles);

        Collection<String> restoredProfiles = new HashSet<>(temporarilyRemovedExplicitProfiles);
        restoredProfiles.retainAll(available);
        temporarilyRemovedExplicitProfiles.removeAll(restoredProfiles);

        explicitProfiles.removeAll(removedProfiles);
        explicitProfiles.addAll(restoredProfiles);
    }

    public Collection<String> getAvailableProfiles() {
        Collection<String> res = new HashSet<>();

        for (MavenProject each : getProjects()) {
            res.addAll(each.getProfilesIds());
        }

        return res;
    }

    public Collection<Pair<String, MavenProfileKind>> getProfilesWithStates() {
        Collection<Pair<String, MavenProfileKind>> result = new LinkedHashSet<>();

        Collection<String> available = new HashSet<>();
        Collection<String> active = new HashSet<>();
        for (MavenProject each : getProjects()) {
            available.addAll(each.getProfilesIds());
            active.addAll(each.getActivatedProfilesIds().getEnabledProfiles());
        }

        Collection<String> enabledProfiles = getExplicitProfiles().getEnabledProfiles();
        Collection<String> disabledProfiles = getExplicitProfiles().getDisabledProfiles();

        for (String each : available) {
            MavenProfileKind state;
            if (disabledProfiles.contains(each)) {
                state = MavenProfileKind.NONE;
            }
            else if (enabledProfiles.contains(each)) {
                state = MavenProfileKind.EXPLICIT;
            }
            else if (active.contains(each)) {
                state = MavenProfileKind.IMPLICIT;
            }
            else {
                state = MavenProfileKind.NONE;
            }
            result.add(Pair.create(each, state));
        }
        return result;
    }

    public void updateAll(boolean force, MavenGeneralSettings generalSettings, MavenProgressIndicator process) {
        List<VirtualFile> managedFiles = getExistingManagedFiles();
        MavenExplicitProfiles explicitProfiles = getExplicitProfiles();

        MavenProjectReader projectReader = new MavenProjectReader();
        update(managedFiles, true, force, explicitProfiles, projectReader, generalSettings, process);

        List<VirtualFile> obsoleteFiles = getRootProjectsFiles();
        if (!obsoleteFiles.isEmpty()) {
            obsoleteFiles.removeAll(managedFiles);
        }
        delete(projectReader, obsoleteFiles, explicitProfiles, generalSettings, process);
    }

    public void update(Collection<VirtualFile> files, boolean force, MavenGeneralSettings generalSettings, MavenProgressIndicator process) {
        update(files, false, force, getExplicitProfiles(), new MavenProjectReader(), generalSettings, process);
    }

    private void update(
        Collection<VirtualFile> files,
        boolean recursive,
        boolean force,
        MavenExplicitProfiles explicitProfiles,
        MavenProjectReader projectReader,
        MavenGeneralSettings generalSettings,
        MavenProgressIndicator process
    ) {
        if (files.isEmpty()) {
            return;
        }

        UpdateContext updateContext = new UpdateContext();
        consulo.util.collection.Stack<MavenProject> updateStack = new Stack<>();

        for (VirtualFile each : files) {
            MavenProject mavenProject = findProject(each);
            if (mavenProject == null) {
                doAdd(each, recursive, explicitProfiles, updateContext, updateStack, projectReader, generalSettings, process);
            }
            else {
                doUpdate(
                    mavenProject,
                    findAggregator(mavenProject),
                    false,
                    recursive,
                    force,
                    explicitProfiles,
                    updateContext,
                    updateStack,
                    projectReader,
                    generalSettings,
                    process
                );
            }
        }

        updateExplicitProfiles();
        updateContext.fireUpdatedIfNecessary();
    }

    private void doAdd(
        final VirtualFile f,
        boolean recursuve,
        MavenExplicitProfiles explicitProfiles,
        UpdateContext updateContext,
        consulo.util.collection.Stack<MavenProject> updateStack,
        MavenProjectReader reader,
        MavenGeneralSettings generalSettings,
        MavenProgressIndicator process
    ) {
        MavenProject newMavenProject = new MavenProject(f);

        MavenProject intendedAggregator = null;
        for (MavenProject each : getProjects()) {
            if (each.getExistingModuleFiles().contains(f)) {
                intendedAggregator = each;
                break;
            }
        }

        doUpdate(
            newMavenProject,
            intendedAggregator,
            true,
            recursuve,
            false,
            explicitProfiles,
            updateContext,
            updateStack,
            reader,
            generalSettings,
            process
        );
    }

    private void doUpdate(
        MavenProject mavenProject,
        MavenProject aggregator,
        boolean isNew,
        boolean recursive,
        boolean force,
        MavenExplicitProfiles explicitProfiles,
        UpdateContext updateContext,
        consulo.util.collection.Stack<MavenProject> updateStack,
        MavenProjectReader reader,
        MavenGeneralSettings generalSettings,
        MavenProgressIndicator process
    ) {
        if (updateStack.contains(mavenProject)) {
            MavenLog.LOG.info("Recursion detected in " + mavenProject.getFile());
            return;
        }
        updateStack.push(mavenProject);

        process.setText(MavenProjectLocalize.mavenReadingPom(mavenProject.getPath()).get());
        process.setText2("");

        List<MavenProject> prevModules = getModules(mavenProject);

        Set<MavenProject> prevInheritors = new HashSet<>();
        if (!isNew) {
            prevInheritors.addAll(findInheritors(mavenProject));
        }

        MavenProjectTimestamp timestamp = calculateTimestamp(mavenProject, explicitProfiles, generalSettings);
        boolean isChanged = force || !timestamp.equals(myTimestamps.get(mavenProject));

        MavenProjectChanges changes = force ? MavenProjectChanges.ALL : MavenProjectChanges.NONE;
        if (isChanged) {
            writeLock();
            try {
                if (!isNew) {
                    clearIDMaps(mavenProject);
                }
            }
            finally {
                writeUnlock();
            }
            MavenId oldParentId = mavenProject.getParentId();
            changes = changes.mergedWith(mavenProject.read(generalSettings, explicitProfiles, reader, myProjectLocator));

            writeLock();
            try {
                myVirtualFileToProjectMapping.put(mavenProject.getFile(), mavenProject);
                fillIDMaps(mavenProject);
            }
            finally {
                writeUnlock();
            }

            if (!Comparing.equal(oldParentId, mavenProject.getParentId())) {
                // ensure timestamp reflects actual parent's timestamp
                timestamp = calculateTimestamp(mavenProject, explicitProfiles, generalSettings);
            }
            myTimestamps.put(mavenProject, timestamp);
        }

        boolean reconnected = isNew;
        if (isNew) {
            connect(aggregator, mavenProject);
        }
        else {
            reconnected = reconnect(aggregator, mavenProject);
        }

        if (isChanged || reconnected) {
            updateContext.update(mavenProject, changes);
        }

        List<VirtualFile> existingModuleFiles = mavenProject.getExistingModuleFiles();
        List<MavenProject> modulesToRemove = new ArrayList<>();
        List<MavenProject> modulesToBecomeRoots = new ArrayList<>();

        for (MavenProject each : prevModules) {
            VirtualFile moduleFile = each.getFile();
            if (!existingModuleFiles.contains(moduleFile)) {
                if (isManagedFile(moduleFile)) {
                    modulesToBecomeRoots.add(each);
                }
                else {
                    modulesToRemove.add(each);
                }
            }
        }
        for (MavenProject each : modulesToRemove) {
            removeModule(mavenProject, each);
            doDelete(mavenProject, each, updateContext);
            prevInheritors.removeAll(updateContext.deletedProjects);
        }

        for (MavenProject each : modulesToBecomeRoots) {
            if (reconnect(null, each)) {
                updateContext.update(each, MavenProjectChanges.NONE);
            }
        }

        for (VirtualFile each : existingModuleFiles) {
            MavenProject module = findProject(each);
            boolean isNewModule = module == null;
            if (isNewModule) {
                module = new MavenProject(each);
            }
            else {
                MavenProject currentAggregator = findAggregator(module);
                if (currentAggregator != null && currentAggregator != mavenProject) {
                    MavenLog.LOG.info("Module " + each + " is already included into " + mavenProject.getFile());
                    continue;
                }
            }

            if (isChanged || isNewModule || recursive) {
                doUpdate(
                    module,
                    mavenProject,
                    isNewModule,
                    recursive,
                    recursive ? force : false,
                    // do not force update modules if only this project was requested to be updated
                    explicitProfiles,
                    updateContext,
                    updateStack,
                    reader,
                    generalSettings,
                    process
                );
            }
            else {
                if (reconnect(mavenProject, module)) {
                    updateContext.update(module, MavenProjectChanges.NONE);
                }
            }
        }

        prevInheritors.addAll(findInheritors(mavenProject));

        for (MavenProject each : prevInheritors) {
            doUpdate(
                each,
                findAggregator(each),
                false,
                false,
                // no need to go recursively in case of inheritance, only when updating modules
                false,
                explicitProfiles,
                updateContext,
                updateStack,
                reader,
                generalSettings,
                process
            );
        }

        updateStack.pop();
    }

    private MavenProjectTimestamp calculateTimestamp(
        final MavenProject mavenProject,
        final MavenExplicitProfiles explicitProfiles,
        final MavenGeneralSettings generalSettings
    ) {
        return ReadAction.compute(() ->
        {
            long pomTimestamp = getFileTimestamp(mavenProject.getFile());
            MavenProject parent = findParent(mavenProject);
            long parentLastReadStamp = parent == null ? -1 : parent.getLastReadStamp();
            VirtualFile profilesXmlFile = mavenProject.getProfilesXmlFile();
            long profilesTimestamp = getFileTimestamp(profilesXmlFile);

            long userSettingsTimestamp = getFileTimestamp(generalSettings.getEffectiveUserSettingsFile());
            long globalSettingsTimestamp = getFileTimestamp(generalSettings.getEffectiveGlobalSettingsFile());

            int profilesHashCode = explicitProfiles.hashCode();

            return new MavenProjectTimestamp(
                pomTimestamp,
                parentLastReadStamp,
                profilesTimestamp,
                userSettingsTimestamp,
                globalSettingsTimestamp,
                profilesHashCode
            );
        });
    }

    private static long getFileTimestamp(VirtualFile file) {
        if (file == null || !file.isValid()) {
            return -1;
        }
        return file.getTimeStamp();
    }

    public boolean isManagedFile(VirtualFile moduleFile) {
        return isManagedFile(moduleFile.getPath());
    }

    public boolean isManagedFile(String path) {
        synchronized (myStateLock) {
            for (String each : myManagedFilesPaths) {
                if (FileUtil.pathsEqual(each, path)) {
                    return true;
                }
            }
            return false;
        }
    }

    public boolean isPotentialProject(String path) {
        if (isManagedFile(path)) {
            return true;
        }

        for (MavenProject each : getProjects()) {
            if (FileUtil.pathsEqual(path, each.getPath())) {
                return true;
            }
            if (each.getModulePaths().contains(path)) {
                return true;
            }
        }
        return false;
    }

    public void delete(List<VirtualFile> files, MavenGeneralSettings generalSettings, MavenProgressIndicator process) {
        delete(new MavenProjectReader(), files, getExplicitProfiles(), generalSettings, process);
    }

    private void delete(
        MavenProjectReader projectReader,
        List<VirtualFile> files,
        MavenExplicitProfiles explicitProfiles,
        MavenGeneralSettings generalSettings,
        MavenProgressIndicator process
    ) {
        if (files.isEmpty()) {
            return;
        }

        UpdateContext updateContext = new UpdateContext();
        consulo.util.collection.Stack<MavenProject> updateStack = new consulo.util.collection.Stack<>();

        Set<MavenProject> inheritorsToUpdate = new HashSet<>();
        for (VirtualFile each : files) {
            MavenProject mavenProject = findProject(each);
            if (mavenProject == null) {
                return;
            }

            inheritorsToUpdate.addAll(findInheritors(mavenProject));
            doDelete(findAggregator(mavenProject), mavenProject, updateContext);
        }
        inheritorsToUpdate.removeAll(updateContext.deletedProjects);

        for (MavenProject each : inheritorsToUpdate) {
            doUpdate(
                each,
                null,
                false,
                false,
                false,
                explicitProfiles,
                updateContext,
                updateStack,
                projectReader,
                generalSettings,
                process
            );
        }

        updateExplicitProfiles();
        updateContext.fireUpdatedIfNecessary();
    }

    private void doDelete(MavenProject aggregator, MavenProject project, UpdateContext updateContext) {
        for (MavenProject each : getModules(project)) {
            if (isManagedFile(each.getPath())) {
                if (reconnect(null, each)) {
                    updateContext.update(each, MavenProjectChanges.NONE);
                }
            }
            else {
                doDelete(project, each, updateContext);
            }
        }

        writeLock();
        try {
            if (aggregator != null) {
                removeModule(aggregator, project);
            }
            else {
                myRootProjects.remove(project);
            }
            myTimestamps.remove(project);
            myVirtualFileToProjectMapping.remove(project.getFile());
            clearIDMaps(project);
            myAggregatorToModuleMapping.remove(project);
            myModuleToAggregatorMapping.remove(project);
        }
        finally {
            writeUnlock();
        }

        updateContext.deleted(project);
    }

    private void fillIDMaps(MavenProject mavenProject) {
        MavenId id = mavenProject.getMavenId();
        myWorkspaceMap.register(id, new File(mavenProject.getFile().getPath()));
        myMavenIdToProjectMapping.put(id, mavenProject);
    }

    private void clearIDMaps(MavenProject mavenProject) {
        MavenId id = mavenProject.getMavenId();
        myWorkspaceMap.unregister(id);
        myMavenIdToProjectMapping.remove(id);
    }

    private void connect(MavenProject newAggregator, MavenProject project) {
        writeLock();
        try {
            if (newAggregator != null) {
                addModule(newAggregator, project);
            }
            else {
                myRootProjects.add(project);
            }
        }
        finally {
            writeUnlock();
        }
    }

    private boolean reconnect(MavenProject newAggregator, MavenProject project) {
        MavenProject prevAggregator = findAggregator(project);

        if (prevAggregator == newAggregator) {
            return false;
        }

        writeLock();
        try {
            if (prevAggregator != null) {
                removeModule(prevAggregator, project);
            }
            else {
                myRootProjects.remove(project);
            }

            if (newAggregator != null) {
                addModule(newAggregator, project);
            }
            else {
                myRootProjects.add(project);
            }
        }
        finally {
            writeUnlock();
        }

        return true;
    }

    public boolean hasProjects() {
        readLock();
        try {
            return !myRootProjects.isEmpty();
        }
        finally {
            readUnlock();
        }
    }

    public List<MavenProject> getRootProjects() {
        readLock();
        try {
            return new ArrayList<>(myRootProjects);
        }
        finally {
            readUnlock();
        }
    }

    private static void updateCrc(CRC32 crc, int x) {
        crc.update(x & 0xFF);
        x >>>= 8;
        crc.update(x & 0xFF);
        x >>>= 8;
        crc.update(x & 0xFF);
        x >>>= 8;
        crc.update(x);
    }

    private static void updateCrc(CRC32 crc, long l) {
        updateCrc(crc, (int)l);
        updateCrc(crc, (int)(l >>> 32));
    }

    private static void updateCrc(CRC32 crc, @Nullable String s) {
        if (s == null) {
            crc.update(111);
        }
        else {
            updateCrc(crc, s.hashCode());
            crc.update(s.length() & 0xFF);
        }
    }

    @Nonnull
    public static Collection<String> getFilterExclusions(MavenProject mavenProject) {
        Element config = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin");
        if (config == null) {
            return Collections.emptySet();
        }
        final List<String> customNonFilteredExtensions =
            MavenJDOMUtil.findChildrenValuesByPath(config, "nonFilteredFileExtensions", "nonFilteredFileExtension");
        if (customNonFilteredExtensions.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableList(customNonFilteredExtensions);
    }

    @RequiredReadAction
    public int getFilterConfigCrc(ProjectFileIndex fileIndex) {
        Application.get().assertReadAccessAllowed();

        readLock();
        try {
            final CRC32 crc = new CRC32();

            MavenExplicitProfiles profiles = myExplicitProfiles;
            if (profiles != null) {
                updateCrc(crc, profiles.hashCode());
            }

            Collection<MavenProject> allProjects = myVirtualFileToProjectMapping.values();

            crc.update(allProjects.size() & 0xFF);
            for (MavenProject mavenProject : allProjects) {
                VirtualFile pomFile = mavenProject.getFile();
                Module module = fileIndex.getModuleForFile(pomFile);
                if (module == null) {
                    continue;
                }

                if (!Comparing.equal(fileIndex.getContentRootForFile(pomFile), pomFile.getParent())) {
                    continue;
                }

                updateCrc(crc, module.getName());

                MavenId mavenId = mavenProject.getMavenId();
                updateCrc(crc, mavenId.getGroupId());
                updateCrc(crc, mavenId.getArtifactId());
                updateCrc(crc, mavenId.getVersion());

                MavenId parentId = mavenProject.getParentId();
                if (parentId != null) {
                    updateCrc(crc, parentId.getGroupId());
                    updateCrc(crc, parentId.getArtifactId());
                    updateCrc(crc, parentId.getVersion());
                }

                updateCrc(crc, mavenProject.getDirectory());
                updateCrc(crc, MavenFilteredPropertyPsiReferenceProvider.getDelimitersPattern(mavenProject).pattern());
                updateCrc(crc, mavenProject.getModelMap().hashCode());
                updateCrc(crc, mavenProject.getResources().hashCode());
                updateCrc(crc, mavenProject.getTestResources().hashCode());
                updateCrc(crc, getFilterExclusions(mavenProject).hashCode());
                updateCrc(crc, mavenProject.getProperties().hashCode());

                for (String each : mavenProject.getFilterPropertiesFiles()) {
                    File file = new File(each);
                    updateCrc(crc, file.lastModified());
                }

                XMLOutputter outputter = new XMLOutputter(Format.getCompactFormat());

                Writer crcWriter = new Writer() {
                    @Override
                    public void write(char[] cbuf, int off, int len) throws IOException {
                        for (int i = off, end = off + len; i < end; i++) {
                            crc.update(cbuf[i]);
                        }
                    }

                    @Override
                    public void flush() throws IOException {
                    }

                    @Override
                    public void close() throws IOException {
                    }
                };

                try {
                    Element resourcePluginCfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin");
                    if (resourcePluginCfg != null) {
                        outputter.output(resourcePluginCfg, crcWriter);
                    }

                    Element warPluginCfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-war-plugin");
                    if (warPluginCfg != null) {
                        outputter.output(warPluginCfg, crcWriter);
                    }
                }
                catch (IOException e) {
                    LOG.error(e);
                }
            }

            return (int)crc.getValue();
        }
        finally {
            readUnlock();
        }
    }

    public List<VirtualFile> getRootProjectsFiles() {
        return MavenUtil.collectFiles(getRootProjects());
    }

    public List<MavenProject> getProjects() {
        readLock();
        try {
            return new ArrayList<>(myVirtualFileToProjectMapping.values());
        }
        finally {
            readUnlock();
        }
    }

    public List<MavenProject> getNonIgnoredProjects() {
        readLock();
        try {
            List<MavenProject> result = new ArrayList<>();
            for (MavenProject each : myVirtualFileToProjectMapping.values()) {
                if (!isIgnored(each)) {
                    result.add(each);
                }
            }
            return result;
        }
        finally {
            readUnlock();
        }
    }

    public List<VirtualFile> getProjectsFiles() {
        readLock();
        try {
            return new ArrayList<>(myVirtualFileToProjectMapping.keySet());
        }
        finally {
            readUnlock();
        }
    }

    @Nullable
    public MavenProject findProject(VirtualFile f) {
        readLock();
        try {
            return myVirtualFileToProjectMapping.get(f);
        }
        finally {
            readUnlock();
        }
    }

    @Nullable
    public MavenProject findProject(MavenId id) {
        readLock();
        try {
            return myMavenIdToProjectMapping.get(id);
        }
        finally {
            readUnlock();
        }
    }

    @Nullable
    public MavenProject findProject(MavenArtifact artifact) {
        return findProject(artifact.getMavenId());
    }

    private MavenWorkspaceMap getWorkspaceMap() {
        readLock();
        try {
            return myWorkspaceMap.copy();
        }
        finally {
            readUnlock();
        }
    }

    public MavenProject findAggregator(MavenProject project) {
        readLock();
        try {
            return myModuleToAggregatorMapping.get(project);
        }
        finally {
            readUnlock();
        }
    }

    public MavenProject findRootProject(@Nonnull MavenProject project) {
        readLock();
        try {
            MavenProject rootProject = project;
            while (true) {
                MavenProject aggregator = myModuleToAggregatorMapping.get(rootProject);
                if (aggregator == null) {
                    return rootProject;
                }
                rootProject = aggregator;
            }
        }
        finally {
            readUnlock();
        }
    }

    public boolean isRootProject(@Nonnull MavenProject project) {
        readLock();
        try {
            return myModuleToAggregatorMapping.get(project) == null;
        }
        finally {
            readUnlock();
        }
    }

    public List<MavenProject> getModules(MavenProject aggregator) {
        readLock();
        try {
            List<MavenProject> modules = myAggregatorToModuleMapping.get(aggregator);
            return modules == null ? Collections.<MavenProject>emptyList() : new ArrayList<>(modules);
        }
        finally {
            readUnlock();
        }
    }

    private void addModule(MavenProject aggregator, MavenProject module) {
        writeLock();
        try {
            List<MavenProject> modules = myAggregatorToModuleMapping.get(aggregator);
            if (modules == null) {
                modules = new ArrayList<>();
                myAggregatorToModuleMapping.put(aggregator, modules);
            }
            modules.add(module);

            myModuleToAggregatorMapping.put(module, aggregator);
        }
        finally {
            writeUnlock();
        }
    }

    private void removeModule(MavenProject aggregator, MavenProject module) {
        writeLock();
        try {
            List<MavenProject> modules = myAggregatorToModuleMapping.get(aggregator);
            if (modules == null) {
                return;
            }
            modules.remove(module);
            myModuleToAggregatorMapping.remove(module);
        }
        finally {
            writeUnlock();
        }
    }

    private MavenProject findParent(MavenProject project) {
        return findProject(project.getParentId());
    }

    public Collection<MavenProject> findInheritors(MavenProject project) {
        readLock();
        try {
            List<MavenProject> result = null;
            MavenId id = project.getMavenId();

            for (MavenProject each : myVirtualFileToProjectMapping.values()) {
                if (each == project) {
                    continue;
                }
                if (id.equals(each.getParentId())) {
                    if (result == null) {
                        result = new ArrayList<>();
                    }
                    result.add(each);
                }
            }

            return result == null ? Collections.<MavenProject>emptyList() : result;
        }
        finally {
            readUnlock();
        }
    }

    public List<MavenProject> getDependentProjects(Collection<MavenProject> projects) {
        readLock();
        try {
            List<MavenProject> result = null;

            Set<MavenCoordinate> projectIds = Sets.newHashSet(new MavenCoordinateHashCodeStrategy());

            for (MavenProject project : projects) {
                projectIds.add(project.getMavenId());
            }

            final Set<File> projectPaths = Sets.newHashSet(FileUtil.FILE_HASHING_STRATEGY);

            for (MavenProject project : projects) {
                projectPaths.add(new File(project.getFile().getPath()));
            }

            for (MavenProject project : myVirtualFileToProjectMapping.values()) {
                boolean isDependent = false;

                Set<String> pathsInStack = project.getModulePaths();
                for (final String path : pathsInStack) {
                    if (projectPaths.contains(new File(path))) {
                        isDependent = true;
                        break;
                    }
                }

                if (!isDependent) {
                    for (MavenArtifact dep : project.getDependencies()) {
                        if (projectIds.contains(dep)) {
                            isDependent = true;
                            break;
                        }
                    }
                }

                if (isDependent) {
                    if (result == null) {
                        result = new ArrayList<>();
                    }
                    result.add(project);
                }
            }

            return result == null ? Collections.<MavenProject>emptyList() : result;
        }
        finally {
            readUnlock();
        }
    }

    @TestOnly
    public void resolve(
        @Nonnull Project project,
        @Nonnull MavenProject mavenProject,
        @Nonnull MavenGeneralSettings generalSettings,
        @Nonnull MavenEmbeddersManager embeddersManager,
        @Nonnull MavenConsole console,
        @Nonnull MavenProgressIndicator process
    ) throws MavenProcessCanceledException {
        resolve(project, mavenProject, generalSettings, embeddersManager, console, new ResolveContext(), process);
    }

    public void resolve(
        @Nonnull Project project,
        @Nonnull MavenProject mavenProject,
        @Nonnull MavenGeneralSettings generalSettings,
        @Nonnull MavenEmbeddersManager embeddersManager,
        @Nonnull MavenConsole console,
        @Nonnull ResolveContext context,
        @Nonnull MavenProgressIndicator process
    ) throws MavenProcessCanceledException {
        MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE);
        embedder.customizeForResolve(getWorkspaceMap(), console, process, generalSettings.isAlwaysUpdateSnapshots());

        try {
            process.checkCanceled();
            process.setText(MavenProjectLocalize.mavenResolvingPom(mavenProject.getDisplayName()).get());
            process.setText2("");
            Pair<MavenProjectChanges, NativeMavenProjectHolder> resolveResult =
                mavenProject.resolve(project, generalSettings, embedder, new MavenProjectReader(), myProjectLocator, context);

            fireProjectResolved(Pair.create(mavenProject, resolveResult.first), resolveResult.second);
        }
        finally {
            embeddersManager.release(embedder);
        }
    }

    public void resolvePlugins(
        @Nonnull MavenProject mavenProject,
        @Nonnull NativeMavenProjectHolder nativeMavenProject,
        @Nonnull MavenEmbeddersManager embeddersManager,
        @Nonnull MavenConsole console,
        @Nonnull MavenProgressIndicator process
    ) throws MavenProcessCanceledException {
        MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_PLUGINS_RESOLVE);
        embedder.customizeForResolve(console, process);
        embedder.clearCachesFor(mavenProject.getMavenId());

        Set<File> filesToRefresh = new HashSet<>();

        try {
            process.setText(MavenProjectLocalize.mavenDownloadingPomPlugins(mavenProject.getDisplayName()).get());

            for (MavenPlugin each : mavenProject.getDeclaredPlugins()) {
                process.checkCanceled();

                Collection<MavenArtifact> artifacts =
                    embedder.resolvePlugin(each, mavenProject.getRemoteRepositories(), nativeMavenProject, false);

                for (MavenArtifact artifact : artifacts) {
                    File pluginJar = artifact.getFile();
                    File pluginDir = pluginJar.getParentFile();
                    if (pluginDir != null) {
                        filesToRefresh.add(pluginDir); // Refresh both *.pom and *.jar files.
                    }
                }
            }

            mavenProject.resetCache();
            firePluginsResolved(mavenProject);
        }
        finally {
            if (filesToRefresh.size() > 0) {
                LocalFileSystem.getInstance().refreshIoFiles(filesToRefresh);
            }

            embeddersManager.release(embedder);
        }
    }

    public void resolveFolders(
        @Nonnull final MavenProject mavenProject,
        @Nonnull final MavenImportingSettings importingSettings,
        @Nonnull final MavenEmbeddersManager embeddersManager,
        @Nonnull final MavenConsole console,
        @Nonnull final MavenProgressIndicator process
    ) throws MavenProcessCanceledException {
        executeWithEmbedder(
            mavenProject,
            embeddersManager,
            MavenEmbeddersManager.FOR_FOLDERS_RESOLVE,
            console,
            process,
            embedder -> {
                process.checkCanceled();
                process.setText(MavenProjectLocalize.mavenUpdatingFoldersPom(mavenProject.getDisplayName()).get());
                process.setText2("");

                Pair<Boolean, MavenProjectChanges> resolveResult = mavenProject.resolveFolders(embedder, importingSettings, console);
                if (resolveResult.first) {
                    fireFoldersResolved(Pair.create(mavenProject, resolveResult.second));
                }
            }
        );
    }

    public MavenArtifactDownloader.DownloadResult downloadSourcesAndJavadocs(
        @Nonnull Project project,
        @Nonnull Collection<MavenProject> projects,
        @Nullable Collection<MavenArtifact> artifacts,
        boolean downloadSources,
        boolean downloadDocs,
        @Nonnull MavenEmbeddersManager embeddersManager,
        @Nonnull MavenConsole console,
        @Nonnull MavenProgressIndicator process
    ) throws MavenProcessCanceledException {
        MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_DOWNLOAD);
        embedder.customizeForResolve(console, process);

        try {
            MavenArtifactDownloader.DownloadResult result =
                MavenArtifactDownloader.download(project, this, projects, artifacts, downloadSources, downloadDocs, embedder, process);

            for (MavenProject each : projects) {
                fireArtifactsDownloaded(each);
            }
            return result;
        }
        finally {
            embeddersManager.release(embedder);
        }
    }

    public void executeWithEmbedder(
        @Nonnull MavenProject mavenProject,
        @Nonnull MavenEmbeddersManager embeddersManager,
        @Nonnull Key embedderKind,
        @Nonnull MavenConsole console,
        @Nonnull MavenProgressIndicator process,
        @Nonnull EmbedderTask task
    ) throws MavenProcessCanceledException {
        MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(embedderKind);
        embedder.customizeForResolve(getWorkspaceMap(), console, process, false);
        embedder.clearCachesFor(mavenProject.getMavenId());
        try {
            task.run(embedder);
        }
        finally {
            embeddersManager.release(embedder);
        }
    }

    public <Result> Result visit(Visitor<Result> visitor) {
        for (MavenProject each : getRootProjects()) {
            if (visitor.isDone()) {
                break;
            }
            doVisit(each, visitor);
        }
        return visitor.getResult();
    }

    private <Result> void doVisit(MavenProject project, Visitor<Result> visitor) {
        if (!visitor.isDone() && visitor.shouldVisit(project)) {
            visitor.visit(project);
            for (MavenProject each : getModules(project)) {
                if (visitor.isDone()) {
                    break;
                }
                doVisit(each, visitor);
            }
            visitor.leave(project);
        }
    }

    private void writeLock() {
        myStructureWriteLock.lock();
    }

    private void writeUnlock() {
        myStructureWriteLock.unlock();
    }

    private void readLock() {
        myStructureReadLock.lock();
    }

    private void readUnlock() {
        myStructureReadLock.unlock();
    }

    public void addListener(Listener l) {
        myListeners.add(l);
    }

    private void fireProfilesChanged() {
        for (Listener each : myListeners) {
            each.profilesChanged();
        }
    }

    private void fireProjectsIgnoredStateChanged(List<MavenProject> ignored, List<MavenProject> unignored, boolean fromImport) {
        for (Listener each : myListeners) {
            each.projectsIgnoredStateChanged(ignored, unignored, fromImport);
        }
    }

    private void fireProjectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
        for (Listener each : myListeners) {
            each.projectsUpdated(updated, deleted);
        }
    }

    private void fireProjectResolved(
        Pair<MavenProject, MavenProjectChanges> projectWithChanges,
        NativeMavenProjectHolder nativeMavenProject
    ) {
        for (Listener each : myListeners) {
            each.projectResolved(projectWithChanges, nativeMavenProject);
        }
    }

    private void firePluginsResolved(MavenProject project) {
        for (Listener each : myListeners) {
            each.pluginsResolved(project);
        }
    }

    private void fireFoldersResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
        for (Listener each : myListeners) {
            each.foldersResolved(projectWithChanges);
        }
    }

    private void fireArtifactsDownloaded(MavenProject project) {
        for (Listener each : myListeners) {
            each.artifactsDownloaded(project);
        }
    }

    private class UpdateContext {
        public final Map<MavenProject, MavenProjectChanges> updatedProjectsWithChanges = new LinkedHashMap<>();
        public final Set<MavenProject> deletedProjects = new LinkedHashSet<>();

        public void update(MavenProject project, MavenProjectChanges changes) {
            deletedProjects.remove(project);
            updatedProjectsWithChanges.put(project, changes.mergedWith(updatedProjectsWithChanges.get(project)));
        }

        public void deleted(MavenProject project) {
            updatedProjectsWithChanges.remove(project);
            deletedProjects.add(project);
        }

        public void deleted(Collection<MavenProject> projects) {
            for (MavenProject each : projects) {
                deleted(each);
            }
        }

        public void fireUpdatedIfNecessary() {
            if (updatedProjectsWithChanges.isEmpty() && deletedProjects.isEmpty()) {
                return;
            }
            List<MavenProject> mavenProjects =
                deletedProjects.isEmpty() ? Collections.<MavenProject>emptyList() : new ArrayList<>(deletedProjects);
            List<Pair<MavenProject, MavenProjectChanges>> updated =
                updatedProjectsWithChanges.isEmpty() ? Collections.<Pair<MavenProject, MavenProjectChanges>>emptyList() : MavenUtil.mapToList
                    (updatedProjectsWithChanges);
            fireProjectsUpdated(updated, mavenProjects);
        }
    }

    public interface EmbedderTask {
        void run(MavenEmbedderWrapper embedder) throws MavenProcessCanceledException;
    }

    public abstract static class Visitor<Result> {
        private Result result;

        public boolean shouldVisit(MavenProject project) {
            return true;
        }

        public abstract void visit(MavenProject project);

        public void leave(MavenProject node) {
        }

        public void setResult(Result result) {
            this.result = result;
        }

        public Result getResult() {
            return result;
        }

        public boolean isDone() {
            return result != null;
        }
    }

    public abstract static class SimpleVisitor extends Visitor<Object> {
    }

    private static class MavenProjectTimestamp {
        private final long myPomTimestamp;
        private final long myParentLastReadStamp;
        private final long myProfilesTimestamp;
        private final long myUserSettingsTimestamp;
        private final long myGlobalSettingsTimestamp;
        private final long myExplicitProfilesHashCode;

        private MavenProjectTimestamp(
            long pomTimestamp,
            long parentLastReadStamp,
            long profilesTimestamp,
            long userSettingsTimestamp,
            long globalSettingsTimestamp,
            long explicitProfilesHashCode
        ) {
            myPomTimestamp = pomTimestamp;
            myParentLastReadStamp = parentLastReadStamp;
            myProfilesTimestamp = profilesTimestamp;
            myUserSettingsTimestamp = userSettingsTimestamp;
            myGlobalSettingsTimestamp = globalSettingsTimestamp;
            myExplicitProfilesHashCode = explicitProfilesHashCode;
        }

        public static MavenProjectTimestamp read(DataInputStream in) throws IOException {
            return new MavenProjectTimestamp(in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readLong());
        }

        public void write(DataOutputStream out) throws IOException {
            out.writeLong(myPomTimestamp);
            out.writeLong(myParentLastReadStamp);
            out.writeLong(myProfilesTimestamp);
            out.writeLong(myUserSettingsTimestamp);
            out.writeLong(myGlobalSettingsTimestamp);
            out.writeLong(myExplicitProfilesHashCode);
        }

        @Override
        public String toString() {
            return "(" + myPomTimestamp + ":" + myParentLastReadStamp + ":" + myProfilesTimestamp + ":" + myUserSettingsTimestamp + ":" + myGlobalSettingsTimestamp + ":" + myExplicitProfilesHashCode
                + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MavenProjectTimestamp timestamp = (MavenProjectTimestamp)o;

            if (myPomTimestamp != timestamp.myPomTimestamp) {
                return false;
            }
            if (myParentLastReadStamp != timestamp.myParentLastReadStamp) {
                return false;
            }
            if (myProfilesTimestamp != timestamp.myProfilesTimestamp) {
                return false;
            }
            if (myUserSettingsTimestamp != timestamp.myUserSettingsTimestamp) {
                return false;
            }
            if (myGlobalSettingsTimestamp != timestamp.myGlobalSettingsTimestamp) {
                return false;
            }
            return myExplicitProfilesHashCode == timestamp.myExplicitProfilesHashCode;
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + (int)(myPomTimestamp ^ (myPomTimestamp >>> 32));
            result = 31 * result + (int)(myParentLastReadStamp ^ (myParentLastReadStamp >>> 32));
            result = 31 * result + (int)(myProfilesTimestamp ^ (myProfilesTimestamp >>> 32));
            result = 31 * result + (int)(myUserSettingsTimestamp ^ (myUserSettingsTimestamp >>> 32));
            result = 31 * result + (int)(myGlobalSettingsTimestamp ^ (myGlobalSettingsTimestamp >>> 32));
            result = 31 * result + (int)(myExplicitProfilesHashCode ^ (myExplicitProfilesHashCode >>> 32));
            return result;
        }
    }

    public interface Listener extends EventListener {
        default void profilesChanged() {
        }

        default void projectsIgnoredStateChanged(List<MavenProject> ignored, List<MavenProject> unignored, boolean fromImport) {
        }

        default void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
        }

        default void projectResolved(
            Pair<MavenProject, MavenProjectChanges> projectWithChanges,
            @Nullable NativeMavenProjectHolder nativeMavenProject
        ) {
        }

        default void pluginsResolved(MavenProject project) {
        }

        default void foldersResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
        }

        default void artifactsDownloaded(MavenProject project) {
        }
    }

    @Deprecated
    public static class ListenerAdapter implements Listener {
        @Override
        public void profilesChanged() {
        }

        @Override
        public void projectsIgnoredStateChanged(List<MavenProject> ignored, List<MavenProject> unignored, boolean fromImport) {
        }

        @Override
        public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
        }

        @Override
        public void projectResolved(
            Pair<MavenProject, MavenProjectChanges> projectWithChanges,
            @Nullable NativeMavenProjectHolder nativeMavenProject
        ) {
        }

        @Override
        public void pluginsResolved(MavenProject project) {
        }

        @Override
        public void foldersResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
        }

        @Override
        public void artifactsDownloaded(MavenProject project) {
        }
    }

    private static class MavenCoordinateHashCodeStrategy implements HashingStrategy<MavenCoordinate> {
        @Override
        public int hashCode(MavenCoordinate object) {
            String artifactId = object.getArtifactId();
            return artifactId == null ? 0 : artifactId.hashCode();
        }

        @Override
        public boolean equals(MavenCoordinate o1, MavenCoordinate o2) {
            return Comparing.equal(o1.getArtifactId(), o2.getArtifactId())
                && Comparing.equal(o1.getVersion(), o2.getVersion())
                && Comparing.equal(o1.getGroupId(), o2.getGroupId());
        }
    }
}
