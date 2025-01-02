// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.project.Project;
import consulo.util.collection.primitive.ints.ConcurrentIntObjectMap;
import consulo.util.collection.primitive.ints.IntMaps;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

public class MavenParsingContext {
    private final MavenRunConfiguration runConfiguration;
    private final ExternalSystemTaskId myTaskId;
    private final Function<String, String> targetFileMapper;

    private List<String> projectsInReactor;
    private final CopyOnWriteArrayList<String> startedProjects = new CopyOnWriteArrayList<>();
    private final Project ideaProject;
    private volatile boolean sessionEnded = false;
    private volatile boolean projectFailure = false;

    private final ConcurrentIntObjectMap<ArrayList<MavenExecutionEntry>> context = IntMaps.newConcurrentIntObjectHashMap();
    private int lastAddedThreadId = 0;

    public MavenParsingContext(MavenRunConfiguration runConfiguration,
                               ExternalSystemTaskId myTaskId,
                               Function<String, String> targetFileMapper) {
        this.runConfiguration = runConfiguration;
        this.myTaskId = myTaskId;
        this.targetFileMapper = targetFileMapper;
        this.ideaProject = runConfiguration.getProject();
    }

    public Function<String, String> getTargetFileMapper() {
        return targetFileMapper;
    }

    public ExternalSystemTaskId getMyTaskId() {
        return myTaskId;
    }

    public void setProjectsInReactor(List<String> projectsInReactor) {
        this.projectsInReactor = projectsInReactor;
    }

    @Deprecated
    public boolean getProjectFailure() {
        return projectFailure;
    }

    public void setProjectFailure(boolean projectFailure) {
        this.projectFailure = projectFailure;
    }

    public boolean isProjectFailure() {
        return projectFailure;
    }

    @Deprecated
    public boolean getSessionEnded() {
        return sessionEnded;
    }

    public boolean isSessionEnded() {
        return sessionEnded;
    }

    public void setSessionEnded(boolean sessionEnded) {
        this.sessionEnded = sessionEnded;
    }

    public Object getLastId() {
        ArrayList<MavenExecutionEntry> entries = context.get(lastAddedThreadId);
        if (entries == null || entries.isEmpty()) {
            return myTaskId;
        }
        else {
            return entries.get(entries.size() - 1).id;
        }
    }

    public List<MavenExecutionEntry> getAllEntriesReversed() {
        ArrayList<MavenExecutionEntry> entries = context.get(lastAddedThreadId);
        return entries != null ? entries : new ArrayList<>();
    }

    public ProjectExecutionEntry getProject(int threadId, String id, boolean create) {
        ProjectExecutionEntry currentProject = search(ProjectExecutionEntry.class, context.get(threadId),
            e -> id == null || e.name.equals(id));

        if (currentProject == null && create) {
            currentProject = new ProjectExecutionEntry(id != null ? id : "", threadId);
            startedProjects.add(removeVersion(currentProject));
            add(threadId, currentProject);
        }
        return currentProject;
    }

    private String removeVersion(ProjectExecutionEntry currentProject) {
        String[] splitted = currentProject.name.split(":");
        if (splitted.length < 3) {
            return currentProject.name;
        }
        return splitted[0] + ":" + splitted[1];
    }

    public ProjectExecutionEntry getProject(int threadId, Map<String, String> parameters, boolean create) {
        return getProject(threadId, parameters.get("id"), create);
    }

    public MojoExecutionEntry getMojo(int threadId, Map<String, String> parameters, boolean create) {
        return getMojo(threadId, parameters, parameters.get("goal"), create);
    }

    private MojoExecutionEntry getMojo(int threadId, Map<String, String> parameters, String name, boolean create) {
        if (name == null) {
            return null;
        }
        MojoExecutionEntry mojo = search(MojoExecutionEntry.class, context.get(threadId), e -> e.name.equals(name));
        if (mojo == null && create) {
            ProjectExecutionEntry currentProject = getProject(threadId, parameters, false);
            mojo = new MojoExecutionEntry(name, threadId, currentProject);
            add(threadId, mojo);
        }
        return mojo;
    }

    public NodeExecutionEntry getNode(int threadId, String name, boolean create) {
        if (name == null) {
            return null;
        }
        NodeExecutionEntry node = search(NodeExecutionEntry.class, context.get(threadId), e -> e.name.equals(name));

        if (node == null && create) {
            MavenExecutionEntry parent = getNodeParent(threadId);
            node = new NodeExecutionEntry(name, threadId, parent);
            add(threadId, node);
        }
        return node;
    }

    private MavenExecutionEntry getNodeParent(int threadId) {
        MojoExecutionEntry mojo = search(MojoExecutionEntry.class, context.get(threadId));
        if (mojo == null) {
            return search(ProjectExecutionEntry.class, context.get(threadId), e -> true);
        }
        return mojo;
    }

    private void add(int id, MavenExecutionEntry entry) {
        ArrayList<MavenExecutionEntry> entries = context.get(id);
        if (entries == null) {
            entries = new ArrayList<>();
            context.put(id, entries);
        }
        lastAddedThreadId = id;
        entries.add(entry);
    }

    public class ProjectExecutionEntry extends MavenExecutionEntry {
        ProjectExecutionEntry(String name, int threadId) {
            super(name, threadId);
        }

        @Override
        public Object getParentId() {
            return MavenParsingContext.this.myTaskId;
        }
    }

    public class MojoExecutionEntry extends MavenExecutionEntry {
        private final ProjectExecutionEntry myProject;

        MojoExecutionEntry(String name, int threadId, ProjectExecutionEntry myProject) {
            super(name, threadId);
            this.myProject = myProject;
        }

        @Override
        public Object getParentId() {
            return myProject != null ? myProject.id : MavenParsingContext.this.myTaskId;
        }
    }

    public class NodeExecutionEntry extends MavenExecutionEntry {
        private final MavenExecutionEntry parent;

        NodeExecutionEntry(String name, int threadId, MavenExecutionEntry parent) {
            super(name, threadId);
            this.parent = parent;
        }

        @Override
        public Object getParentId() {
            return parent != null ? parent.id : MavenParsingContext.this.myTaskId;
        }
    }

    private <T extends MavenExecutionEntry> T search(Class<T> klass, ArrayList<MavenExecutionEntry> entries) {
        return search(klass, entries, e -> true);
    }

    private <T extends MavenExecutionEntry> T search(Class<T> klass, List<MavenExecutionEntry> entries, Predicate<T> filter) {
        if (entries == null) {
            return null;
        }
        for (int j = entries.size() - 1; j >= 0; j--) {
            MavenExecutionEntry entry = entries.get(j);
            if (klass.isAssignableFrom(entry.getClass())) {
                @SuppressWarnings("unchecked")
                T castedEntry = (T) entry;
                if (filter.test(castedEntry)) {
                    return castedEntry;
                }
            }
        }
        return null;
    }

    public abstract class MavenExecutionEntry {
        public final String name;
        private final int myThreadId;
        public final Object id = new Object();

        MavenExecutionEntry(String name, int myThreadId) {
            this.name = name;
            this.myThreadId = myThreadId;
        }

        public Object getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public abstract Object getParentId();

        public void complete() {
            ArrayList<MavenExecutionEntry> entries = MavenParsingContext.this.context.get(myThreadId);
            if (entries != null) {
                entries.remove(this);
            }
        }
    }
}

