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
package org.jetbrains.idea.maven.indices;

import consulo.application.ApplicationManager;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.lang.StringUtil;
import consulo.ui.ex.awt.TabbedPaneWrapper;
import consulo.util.lang.Pair;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import consulo.maven.rt.server.common.model.MavenId;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.List;
import java.util.*;

public class MavenArtifactSearchDialog extends DialogWrapper {
    private List<MavenId> myResult = Collections.emptyList();

    public static List<MavenId> ourResultForTest;

    private TabbedPaneWrapper myTabbedPane;
    private MavenArtifactSearchPanel myArtifactsPanel;
    private MavenArtifactSearchPanel myClassesPanel;

    private final Map<Pair<String, String>, String> myManagedDependenciesMap = new HashMap<>();

    private final Map<MavenArtifactSearchPanel, Boolean> myOkButtonStates = new HashMap<>();

    @Nonnull
    public static List<MavenId> searchForClass(Project project, String className) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            assert ourResultForTest != null;

            List<MavenId> res = ourResultForTest;
            ourResultForTest = null;
            return res;
        }

        MavenArtifactSearchDialog d = new MavenArtifactSearchDialog(project, className, true);
        d.show();
        if (!d.isOK()) {
            return Collections.emptyList();
        }

        return d.getResult();
    }

    @Nonnull
    public static List<MavenId> searchForArtifact(Project project, Collection<MavenDomDependency> managedDependencies) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            assert ourResultForTest != null;

            List<MavenId> res = ourResultForTest;
            ourResultForTest = null;
            return res;
        }

        MavenArtifactSearchDialog d = new MavenArtifactSearchDialog(project, "", false);
        d.setManagedDependencies(managedDependencies);

        d.show();
        if (!d.isOK()) {
            return Collections.emptyList();
        }

        return d.getResult();
    }

    public void setManagedDependencies(Collection<MavenDomDependency> managedDependencies) {
        myManagedDependenciesMap.clear();

        for (MavenDomDependency dependency : managedDependencies) {
            String groupId = dependency.getGroupId().getStringValue();
            String artifactId = dependency.getArtifactId().getStringValue();
            String version = dependency.getVersion().getStringValue();

            if (StringUtil.isNotEmpty(groupId) && StringUtil.isNotEmpty(artifactId) && StringUtil.isNotEmpty(version)) {
                myManagedDependenciesMap.put(Pair.create(groupId, artifactId), version);
            }
        }
    }

    private MavenArtifactSearchDialog(Project project, String initialText, boolean classMode) {
        super(project, true);

        initComponents(project, initialText, classMode);

        setTitle("Maven Artifact Search");
        updateOkButtonState();
        init();

        myArtifactsPanel.scheduleSearch();
        myClassesPanel.scheduleSearch();
    }

    private void initComponents(Project project, String initialText, boolean classMode) {
        myTabbedPane = new TabbedPaneWrapper(project);

        MavenArtifactSearchPanel.Listener listener = new MavenArtifactSearchPanel.Listener() {
            public void itemSelected() {
                clickDefaultButton();
            }

            public void canSelectStateChanged(MavenArtifactSearchPanel from, boolean canSelect) {
                myOkButtonStates.put(from, canSelect);
                updateOkButtonState();
            }
        };

        myArtifactsPanel =
            new MavenArtifactSearchPanel(project, !classMode ? initialText : "", false, listener, this, myManagedDependenciesMap);
        myClassesPanel =
            new MavenArtifactSearchPanel(project, classMode ? initialText : "", true, listener, this, myManagedDependenciesMap);

        myTabbedPane.addTab("Search for artifact", myArtifactsPanel);
        myTabbedPane.addTab("Search for class", myClassesPanel);
        myTabbedPane.setSelectedIndex(classMode ? 1 : 0);

        myTabbedPane.getComponent().setPreferredSize(new Dimension(900, 600));

        myTabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateOkButtonState();
            }
        });

        updateOkButtonState();
    }

    private void updateOkButtonState() {
        Boolean canSelect = myOkButtonStates.get(myTabbedPane.getSelectedComponent());
        if (canSelect == null) {
            canSelect = false;
        }
        setOKActionEnabled(canSelect);
    }

    @Nonnull
    @Override
    protected Action getOKAction() {
        Action result = super.getOKAction();
        result.putValue(Action.NAME, "Add");
        return result;
    }

    protected JComponent createCenterPanel() {
        return myTabbedPane.getComponent();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myTabbedPane.getSelectedIndex() == 0 ? myArtifactsPanel.getSearchField() : myClassesPanel.getSearchField();
    }

    @Override
    protected String getDimensionServiceKey() {
        return "Maven.ArtifactSearchDialog";
    }

    @Nonnull
    public List<MavenId> getResult() {
        return myResult;
    }

    @Override
    protected void doOKAction() {
        MavenArtifactSearchPanel panel = myTabbedPane.getSelectedIndex() == 0 ? myArtifactsPanel : myClassesPanel;
        myResult = panel.getResult();
        super.doOKAction();
    }
}
