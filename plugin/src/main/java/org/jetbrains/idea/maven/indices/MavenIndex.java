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
package org.jetbrains.idea.maven.indices;

import consulo.index.io.EnumeratorStringDescriptor;
import consulo.index.io.PersistentEnumeratorBase;
import consulo.index.io.PersistentHashMap;
import consulo.index.io.data.DataExternalizer;
import consulo.maven.rt.server.common.model.MavenArtifactInfo;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.maven.rt.server.common.server.IndexedMavenId;
import consulo.maven.rt.server.common.server.MavenServerIndexerException;
import consulo.util.io.FilePermissionCopier;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.lazy.LazyValue;
import org.apache.lucene.search.Query;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.server.MavenIndexerWrapper;
import org.jetbrains.idea.maven.server.MavenIndicesProcessor;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class MavenIndex {
    private static final String CURRENT_VERSION = "4";

    protected static final String INDEX_INFO_FILE = "index.properties";

    private static final String INDEX_VERSION_KEY = "version";
    private static final String KIND_KEY = "kind";
    private static final String ID_KEY = "id";
    private static final String PATH_OR_URL_KEY = "pathOrUrl";
    private static final String TIMESTAMP_KEY = "lastUpdate";
    private static final String DATA_DIR_NAME_KEY = "dataDirName";
    private static final String FAILURE_MESSAGE_KEY = "failureMessage";

    private static final String DATA_DIR_PREFIX = "data";

    private static final String ARTIFACT_IDS_MAP_FILE = "artifactIds-map.dat";
    private static final String VERSIONS_MAP_FILE = "versions-map.dat";

    public enum Kind {
        LOCAL,
        REMOTE
    }

    private final MavenIndexerWrapper myNexusIndexer;
    private final NotNexusIndexer myNotNexusIndexer;
    private final File myDir;

    private final Set<String> myRegisteredRepositoryIds = new HashSet<>();
    private final LazyValue<String> myId =
        LazyValue.notNullWithModCount(() -> StringUtil.join(myRegisteredRepositoryIds, ","), myRegisteredRepositoryIds::hashCode);

    private final String myRepositoryPathOrUrl;
    private final Kind myKind;
    private Long myUpdateTimestamp;

    private String myDataDirName;
    private IndexData myData;

    private String myFailureMessage;

    private boolean isBroken;
    private final IndexListener myListener;

    public MavenIndex(
        MavenIndexerWrapper nexusIndexer,
        File dir,
        String repositoryId,
        String repositoryPathOrUrl,
        Kind kind,
        IndexListener listener
    ) throws MavenIndexException {
        myNexusIndexer = nexusIndexer;
        myDir = dir;
        myRegisteredRepositoryIds.add(repositoryId);
        myRepositoryPathOrUrl = normalizePathOrUrl(repositoryPathOrUrl);
        myKind = kind;
        myListener = listener;

        myNotNexusIndexer = initNotNexusIndexer(kind, repositoryPathOrUrl);

        open();
    }

    public MavenIndex(MavenIndexerWrapper nexusIndexer, File dir, IndexListener listener) throws MavenIndexException {
        myNexusIndexer = nexusIndexer;
        myDir = dir;
        myListener = listener;

        Properties props = new Properties();
        try {
            try (FileInputStream s = new FileInputStream(new File(dir, INDEX_INFO_FILE))) {
                props.load(s);
            }
        }
        catch (IOException e) {
            throw new MavenIndexException("Cannot read " + INDEX_INFO_FILE + " file", e);
        }

        if (!CURRENT_VERSION.equals(props.getProperty(INDEX_VERSION_KEY))) {
            throw new MavenIndexException("Incompatible index version, needs to be updated: " + dir);
        }

        myKind = Kind.valueOf(props.getProperty(KIND_KEY));

        String myRepositoryIdsStr = props.getProperty(ID_KEY);
        if (myRepositoryIdsStr != null) {
            myRegisteredRepositoryIds.addAll(StringUtil.split(myRepositoryIdsStr, ","));
        }
        myRepositoryPathOrUrl = normalizePathOrUrl(props.getProperty(PATH_OR_URL_KEY));

        try {
            String timestamp = props.getProperty(TIMESTAMP_KEY);
            if (timestamp != null) {
                myUpdateTimestamp = Long.parseLong(timestamp);
            }
        }
        catch (Exception ignored) {
        }

        myDataDirName = props.getProperty(DATA_DIR_NAME_KEY);
        myFailureMessage = props.getProperty(FAILURE_MESSAGE_KEY);

        myNotNexusIndexer = initNotNexusIndexer(myKind, myRepositoryPathOrUrl);

        open();
    }

    private static NotNexusIndexer initNotNexusIndexer(Kind kind, String repositoryPathOrUrl) {
        if (kind == Kind.REMOTE) {
            try {
                URL url = new URL(repositoryPathOrUrl);

                String host = url.getHost();
                if (host != null) {
                    List<String> path = StringUtil.split(StringUtil.trimStart(url.getPath(), "/"), "/");
                    if (host.equals("dl.bintray.com")) {
                        if (path.size() > 1) {
                            return new BintrayIndexer(path.get(0), path.get(1));
                        }
                    }
                    else if (host.endsWith(".bintray.com") && !path.isEmpty()) {
                        return new BintrayIndexer(StringUtil.trimEnd(host, ".bintray.com"), path.get(0));
                    }
                }
            }
            catch (MalformedURLException ignored) {
            }
        }
        return null;
    }

    public void registerId(String repositoryId) throws MavenIndexException {
        if (myRegisteredRepositoryIds.add(repositoryId)) {
            save();
            close(true);
            open();
        }
    }

    @Nonnull
    public static String normalizePathOrUrl(@Nonnull String pathOrUrl) {
        pathOrUrl = pathOrUrl.trim();
        pathOrUrl = FileUtil.toSystemIndependentName(pathOrUrl);
        while (pathOrUrl.endsWith("/")) {
            pathOrUrl = pathOrUrl.substring(0, pathOrUrl.length() - 1);
        }
        return pathOrUrl;
    }

    private void open() throws MavenIndexException {
        try {
            try {
                doOpen();
            }
            catch (Exception e1) {
                final boolean versionUpdated = e1.getCause() instanceof PersistentEnumeratorBase.VersionUpdatedException;
                if (!versionUpdated) {
                    MavenLog.LOG.warn(e1);
                }

                try {
                    doOpen();
                }
                catch (Exception e2) {
                    throw new MavenIndexException("Cannot open index " + myDir.getPath(), e2);
                }
                markAsBroken();
            }
        }
        finally {
            save();
        }
    }

    private void doOpen() throws Exception {
        try {
            File dataDir;
            if (myDataDirName == null) {
                dataDir = createNewDataDir();
                myDataDirName = dataDir.getName();
            }
            else {
                dataDir = new File(myDir, myDataDirName);
                dataDir.mkdirs();
            }
            myData = new IndexData(dataDir);
        }
        catch (Exception e) {
            cleanupBrokenData();
            throw e;
        }
    }

    private void cleanupBrokenData() {
        close(true);

        //noinspection TestOnlyProblems
        final File currentDataDir = getCurrentDataDir();
        final File currentDataContextDir = getCurrentDataContextDir();
        final File[] files = currentDataDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!FileUtil.filesEqual(file, currentDataContextDir)) {
                    FileUtil.delete(file);
                }
            }
        }
        else {
            FileUtil.delete(currentDataDir);
        }
    }

    public synchronized void close(boolean releaseIndexContext) {
        try {
            if (myData != null) {
                myData.close(releaseIndexContext);
            }
        }
        catch (MavenIndexException e) {
            MavenLog.LOG.warn(e);
        }
        myData = null;
    }

    private synchronized void save() {
        myDir.mkdirs();

        Properties props = new Properties();

        props.setProperty(KIND_KEY, myKind.toString());
        props.setProperty(ID_KEY, myId.get());
        props.setProperty(PATH_OR_URL_KEY, myRepositoryPathOrUrl);
        props.setProperty(INDEX_VERSION_KEY, CURRENT_VERSION);
        if (myUpdateTimestamp != null) {
            props.setProperty(TIMESTAMP_KEY, String.valueOf(myUpdateTimestamp));
        }
        if (myDataDirName != null) {
            props.setProperty(DATA_DIR_NAME_KEY, myDataDirName);
        }
        if (myFailureMessage != null) {
            props.setProperty(FAILURE_MESSAGE_KEY, myFailureMessage);
        }

        try {
            FileOutputStream s = new FileOutputStream(new File(myDir, INDEX_INFO_FILE));
            try {
                props.store(s, null);
            }
            finally {
                s.close();
            }
        }
        catch (IOException e) {
            MavenLog.LOG.warn(e);
        }
    }

    public String getRepositoryId() {
        return myId.get();
    }

    public File getRepositoryFile() {
        return myKind == Kind.LOCAL ? new File(myRepositoryPathOrUrl) : null;
    }

    public String getRepositoryUrl() {
        return myKind == Kind.REMOTE ? myRepositoryPathOrUrl : null;
    }

    public String getRepositoryPathOrUrl() {
        return myRepositoryPathOrUrl;
    }

    public Kind getKind() {
        return myKind;
    }

    public boolean isFor(Kind kind, String pathOrUrl) {
        if (myKind != kind) {
            return false;
        }
        if (kind == Kind.LOCAL) {
            return FileUtil.pathsEqual(myRepositoryPathOrUrl, normalizePathOrUrl(pathOrUrl));
        }
        return myRepositoryPathOrUrl.equalsIgnoreCase(normalizePathOrUrl(pathOrUrl));
    }

    public synchronized long getUpdateTimestamp() {
        return myUpdateTimestamp == null ? -1 : myUpdateTimestamp;
    }

    public synchronized String getFailureMessage() {
        return myFailureMessage;
    }

    public void updateOrRepair(boolean fullUpdate, MavenGeneralSettings settings, MavenProgressIndicator progress)
        throws MavenProcessCanceledException {
        try {
            final File newDataDir = createNewDataDir();
            final File newDataContextDir = getDataContextDir(newDataDir);
            final File currentDataContextDir = getCurrentDataContextDir();

            if (myNotNexusIndexer == null) {
                boolean reuseExistingContext =
                    fullUpdate ? myKind != Kind.LOCAL && hasValidContext(currentDataContextDir) : hasValidContext(currentDataContextDir);

                fullUpdate = fullUpdate || !reuseExistingContext && myKind == Kind.LOCAL;

                if (reuseExistingContext) {
                    try {
                        FileUtil.copyDir(currentDataContextDir, newDataContextDir, FilePermissionCopier.BY_NIO2);
                    }
                    catch (IOException e) {
                        throw new MavenIndexException(e);
                    }
                }

                if (fullUpdate) {
                    int context = createContext(newDataContextDir, "update");
                    try {
                        updateContext(context, settings, progress);
                    }
                    finally {
                        myNexusIndexer.releaseIndex(context);
                    }
                }
            }

            updateData(progress, newDataDir, fullUpdate);

            isBroken = false;
            myFailureMessage = null;
        }
        catch (MavenProcessCanceledException e) {
            throw e;
        }
        catch (Exception e) {
            handleUpdateException(e);
        }

        save();
    }

    private boolean hasValidContext(@Nonnull File contextDir) {
        return contextDir.isDirectory() && myNexusIndexer.indexExists(contextDir);
    }

    private void handleUpdateException(Exception e) {
        myFailureMessage = e.getMessage();
        MavenLog.LOG.warn("Failed to update Maven indices for: [" + myId + "] " + myRepositoryPathOrUrl, e);
    }

    private int createContext(File contextDir, String suffix) throws MavenServerIndexerException {
        if (myNotNexusIndexer != null) {
            return 0;
        }

        String indexId = myDir.getName() + "-" + suffix;
        return myNexusIndexer.createIndex(indexId, myId.get(), getRepositoryFile(), getRepositoryUrl(), contextDir);
    }

    private void updateContext(int indexId, MavenGeneralSettings settings, MavenProgressIndicator progress)
        throws MavenServerIndexerException, MavenProcessCanceledException {
        myNexusIndexer.updateIndex(indexId, settings, progress);
    }

    private void updateData(MavenProgressIndicator progress, File newDataDir, boolean fullUpdate) throws MavenIndexException {
        IndexData newData = new IndexData(newDataDir);
        try {
            doUpdateIndexData(newData, progress);
            newData.flush();
        }
        catch (Throwable e) {
            newData.close(true);
            FileUtil.delete(newDataDir);

            if (e instanceof MavenServerIndexerException) {
                throw new MavenIndexException(e);
            }
            if (e instanceof IOException) {
                throw new MavenIndexException(e);
            }
            throw new RuntimeException(e);
        }

        synchronized (this) {
            IndexData oldData = myData;

            myData = newData;
            myDataDirName = newDataDir.getName();

            if (fullUpdate) {
                myUpdateTimestamp = System.currentTimeMillis();
            }

            oldData.close(true);

            File[] files = myDir.listFiles();
            if (files != null) {
                for (File each : files) {
                    if (each.getName().startsWith(DATA_DIR_PREFIX) && !each.getName().equals(myDataDirName)) {
                        FileUtil.delete(each);
                    }
                }
            }
        }
    }

    private void doUpdateIndexData(IndexData data, MavenProgressIndicator progress) throws IOException, MavenServerIndexerException {
        final Map<String, Set<String>> groupToArtifactMap = new HashMap<>();
        final Map<String, Set<String>> groupWithArtifactToVersionMap = new HashMap<>();

        final StringBuilder builder = new StringBuilder();

        progress.pushState();
        progress.setIndeterminate(true);

        try {
            MavenIndicesProcessor processor = artifacts -> {
                for (IndexedMavenId each : artifacts) {
                    String groupId = each.groupId;
                    String artifactId = each.artifactId;
                    String version = each.version;

                    builder.setLength(0);

                    builder.append(groupId).append(":").append(artifactId);
                    String ga = builder.toString();

                    getOrCreate(groupToArtifactMap, groupId).add(artifactId);
                    getOrCreate(groupWithArtifactToVersionMap, ga).add(version);
                }
            };

            if (myNotNexusIndexer != null) {
                myNotNexusIndexer.processArtifacts(progress, processor);
            }
            else {
                myNexusIndexer.processArtifacts(data.indexId, processor);
            }

            persist(groupToArtifactMap, data.groupToArtifactMap);
            persist(groupWithArtifactToVersionMap, data.groupWithArtifactToVersionMap);
        }
        finally {
            progress.popState();
        }
    }

    private static <T> Set<T> getOrCreate(Map<String, Set<T>> map, String key) {
        Set<T> result = map.get(key);
        if (result == null) {
            result = new HashSet<>();
            map.put(key, result);
        }
        return result;
    }

    private static <T> void persist(Map<String, T> map, PersistentHashMap<String, T> persistentMap) throws IOException {
        for (Map.Entry<String, T> each : map.entrySet()) {
            persistentMap.put(each.getKey(), each.getValue());
        }
    }

    @TestOnly
    public File getDir() {
        return myDir;
    }

    @TestOnly
    protected synchronized File getCurrentDataDir() {
        return new File(myDir, myDataDirName);
    }

    private File getCurrentDataContextDir() {
        //noinspection TestOnlyProblems
        return new File(getCurrentDataDir(), "context");
    }

    private static File getDataContextDir(File dataDir) {
        return new File(dataDir, "context");
    }

    @Nonnull
    private File createNewDataDir() {
        return MavenIndices.createNewDir(myDir, DATA_DIR_PREFIX, 100);
    }

    public synchronized void addArtifact(final File artifactFile) {
        doIndexTask(
            () -> {
                MavenId id = myData.addArtifact(artifactFile);

                String groupId = id.getGroupId();
                String artifactId = id.getArtifactId();
                String version = id.getVersion();

                myData.hasGroupCache.put(groupId, true);

                String groupWithArtifact = groupId + ":" + artifactId;

                myData.hasArtifactCache.put(groupWithArtifact, true);
                myData.hasVersionCache.put(groupWithArtifact + ':' + version, true);

                addToCache(myData.groupToArtifactMap, groupId, artifactId);
                addToCache(myData.groupWithArtifactToVersionMap, groupWithArtifact, version);
                myData.flush();

                return null;
            },
            null
        );
    }

    private static void addToCache(PersistentHashMap<String, Set<String>> cache, String key, String value) throws IOException {
        Set<String> values = cache.get(key);
        if (values == null) {
            values = new HashSet<>();
        }
        values.add(value);
        cache.put(key, values);
    }

    public synchronized Collection<String> getGroupIds() {
        return doIndexTask(() -> myData.groupToArtifactMap.getAllDataObjects(null), Collections.<String>emptySet());
    }

    public synchronized Set<String> getArtifactIds(final String groupId) {
        return doIndexTask(
            () -> {
                Set<String> result = myData.groupToArtifactMap.get(groupId);
                return result == null ? Collections.<String>emptySet() : result;
            },
            Collections.<String>emptySet()
        );
    }

    @TestOnly
    public synchronized void printInfo() {
        doIndexTask(
            () -> {
                System.out.println("BaseFile: " + myData.groupToArtifactMap.getBaseFile());
                System.out.println("All data objects: " + myData.groupToArtifactMap.getAllDataObjects(null));
                return Collections.<String>emptySet();
            },
            Collections.<String>emptySet()
        );
    }

    public synchronized Set<String> getVersions(final String groupId, final String artifactId) {
        return doIndexTask(
            () -> {
                Set<String> result = myData.groupWithArtifactToVersionMap.get(groupId + ":" + artifactId);
                return result == null ? Collections.<String>emptySet() : result;
            },
            Collections.<String>emptySet()
        );
    }

    public synchronized boolean hasGroupId(String groupId) {
        if (isBroken) {
            return false;
        }

        return hasValue(myData.groupToArtifactMap, myData.hasGroupCache, groupId);
    }

    public synchronized boolean hasArtifactId(String groupId, String artifactId) {
        if (isBroken) {
            return false;
        }

        return hasValue(myData.groupWithArtifactToVersionMap, myData.hasArtifactCache, groupId + ":" + artifactId);
    }

    public synchronized boolean hasVersion(String groupId, String artifactId, final String version) {
        if (isBroken) {
            return false;
        }

        final String groupWithArtifactWithVersion = groupId + ":" + artifactId + ':' + version;

        Boolean res = myData.hasVersionCache.get(groupWithArtifactWithVersion);
        if (res == null) {
            res = doIndexTask(() -> {
                String groupWithVersion =
                    groupWithArtifactWithVersion.substring(0, groupWithArtifactWithVersion.length() - version.length() - 1);
                Set<String> set = myData.groupWithArtifactToVersionMap.get(groupWithVersion);
                return set != null && set.contains(version);
            }, false);

            myData.hasVersionCache.put(groupWithArtifactWithVersion, res);
        }

        return res;
    }

    private boolean hasValue(final PersistentHashMap<String, ?> map, Map<String, Boolean> cache, final String value) {
        Boolean res = cache.get(value);
        if (res == null) {
            res = doIndexTask(() -> map.tryEnumerate(value) != 0, false).booleanValue();

            cache.put(value, res);
        }

        return res;
    }

    public synchronized Set<MavenArtifactInfo> search(final Query query, final int maxResult) {
        if (myNotNexusIndexer != null) {
            return Collections.emptySet();
        }

        return doIndexTask(() -> myData.search(query, maxResult), Collections.<MavenArtifactInfo>emptySet());
    }

    private <T> T doIndexTask(IndexTask<T> task, T defaultValue) {
        assert Thread.holdsLock(this);

        if (!isBroken) {
            try {
                return task.doTask();
            }
            catch (Exception e1) {
                MavenLog.LOG.warn(e1);

                cleanupBrokenData();
                try {
                    open();
                }
                catch (MavenIndexException e2) {
                    MavenLog.LOG.warn(e2);
                }
            }
        }
        markAsBroken();
        return defaultValue;
    }

    private void markAsBroken() {
        if (!isBroken) {
            myListener.indexIsBroken(this);
        }
        isBroken = true;
    }

    private interface IndexTask<T> {
        T doTask() throws Exception;
    }

    private class IndexData {
        final PersistentHashMap<String, Set<String>> groupToArtifactMap;
        final PersistentHashMap<String, Set<String>> groupWithArtifactToVersionMap;

        final Map<String, Boolean> hasGroupCache = new HashMap<>();
        final Map<String, Boolean> hasArtifactCache = new HashMap<>();
        final Map<String, Boolean> hasVersionCache = new HashMap<>();

        private final int indexId;

        public IndexData(File dir) throws MavenIndexException {
            try {
                groupToArtifactMap = createPersistentMap(new File(dir, ARTIFACT_IDS_MAP_FILE));
                groupWithArtifactToVersionMap = createPersistentMap(new File(dir, VERSIONS_MAP_FILE));

                indexId = createContext(getDataContextDir(dir), dir.getName());
            }
            catch (IOException e) {
                close(true);
                throw new MavenIndexException(e);
            }
            catch (MavenServerIndexerException e) {
                close(true);
                throw new MavenIndexException(e);
            }
        }

        private PersistentHashMap<String, Set<String>> createPersistentMap(final File f) throws IOException {
            return new PersistentHashMap<>(f, new EnumeratorStringDescriptor(), new SetDescriptor());
        }

        public void close(boolean releaseIndexContext) throws MavenIndexException {
            MavenIndexException[] exceptions = new MavenIndexException[1];

            try {
                if (indexId != 0 && releaseIndexContext) {
                    myNexusIndexer.releaseIndex(indexId);
                }
            }
            catch (MavenServerIndexerException e) {
                MavenLog.LOG.warn(e);
                if (exceptions[0] == null) {
                    exceptions[0] = new MavenIndexException(e);
                }
            }

            safeClose(groupToArtifactMap, exceptions);
            safeClose(groupWithArtifactToVersionMap, exceptions);

            if (exceptions[0] != null) {
                throw exceptions[0];
            }
        }

        private void safeClose(@Nullable Closeable enumerator, MavenIndexException[] exceptions) {
            try {
                if (enumerator != null) {
                    enumerator.close();
                }
            }
            catch (IOException e) {
                MavenLog.LOG.warn(e);
                if (exceptions[0] == null) {
                    exceptions[0] = new MavenIndexException(e);
                }
            }
        }

        public void flush() throws IOException {
            groupToArtifactMap.force();
            groupWithArtifactToVersionMap.force();
        }

        public MavenId addArtifact(File artifactFile) throws MavenServerIndexerException {
            return myNexusIndexer.addArtifact(indexId, artifactFile);
        }

        public Set<MavenArtifactInfo> search(Query query, int maxResult) throws MavenServerIndexerException {
            return myNexusIndexer.search(indexId, query, maxResult);
        }
    }

    private static class SetDescriptor implements DataExternalizer<Set<String>> {
        @Override
        public void save(@Nonnull DataOutput s, Set<String> set) throws IOException {
            s.writeInt(set.size());
            for (String each : set) {
                s.writeUTF(each);
            }
        }

        @Override
        public Set<String> read(@Nonnull DataInput s) throws IOException {
            int count = s.readInt();
            Set<String> result = new HashSet<>(count);
            while (count-- > 0) {
                result.add(s.readUTF());
            }
            return result;
        }
    }

    public interface IndexListener {
        void indexIsBroken(MavenIndex index);
    }
}
