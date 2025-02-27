/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import consulo.application.AllIcons;
import consulo.application.util.DateFormatUtil;
import consulo.configurable.BaseConfigurable;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.InputValidator;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.util.ListUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenIndicesLocalize;
import org.jetbrains.idea.maven.services.MavenRepositoryServicesManager;
import org.jetbrains.idea.maven.utils.library.RepositoryAttachHandler;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenRepositoriesConfigurable extends BaseConfigurable implements SearchableConfigurable, Configurable.NoScroll {
    private final MavenProjectIndicesManager myManager;

    private JPanel myMainPanel;
    private JBTable myIndicesTable;
    private JButton myUpdateButton;
    private JButton myRemoveButton;
    private JButton myAddButton;
    private JBList myServiceList;
    private JButton myTestButton;
    private JButton myEditButton;

    private AsyncProcessIcon myUpdatingIcon;
    private Timer myRepaintTimer;
    private ActionListener myTimerListener;
    private final Project myProject;
    private final CollectionListModel<String> myModel = new CollectionListModel<>();

    public MavenRepositoriesConfigurable(Project project) {
        myProject = project;
        myManager = MavenProjectIndicesManager.getInstance(project);
        configControls();
    }

    @Override
    @RequiredUIAccess
    public boolean isModified() {
        return !myModel.getItems().equals(MavenRepositoryServicesManager.getInstance().getUrls());
    }

    private void configControls() {
        myServiceList.setModel(myModel);
        myServiceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myAddButton.addActionListener(e -> {
            final String value = (String)myServiceList.getSelectedValue();
            @SuppressWarnings("RequiredXAction")
            final String text = Messages.showInputDialog(
                "Artifactory or Nexus Service URL",
                "Add Service URL",
                UIUtil.getQuestionIcon(),
                value == null ? "http://" : value,
                new URLInputVaslidator()
            );
            if (StringUtil.isNotEmpty(text)) {
                myModel.add(text);
                myServiceList.setSelectedValue(text, true);
            }
        });
        myEditButton.addActionListener(e -> {
            final int index = myServiceList.getSelectedIndex();
            @SuppressWarnings("RequiredXAction")
            final String text = Messages.showInputDialog(
                "Artifactory or Nexus Service URL",
                "Edit Service URL",
                UIUtil.getQuestionIcon(),
                myModel.getElementAt(index),
                new URLInputVaslidator()
            );
            if (StringUtil.isNotEmpty(text)) {
                myModel.setElementAt(text, index);
            }
        });
        ListUtil.addRemoveListener(myRemoveButton, myServiceList);
        ListUtil.disableWhenNoSelection(myTestButton, myServiceList);
        ListUtil.disableWhenNoSelection(myEditButton, myServiceList);
        myTestButton.addActionListener(e -> {
            final String value = (String)myServiceList.getSelectedValue();
            if (value != null) {
                testServiceConnection(value);
            }
        });

        myUpdateButton.addActionListener(e -> doUpdateIndex());

        myIndicesTable.getSelectionModel().addListSelectionListener(e -> updateButtonsState());

        myIndicesTable.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int row = myIndicesTable.rowAtPoint(e.getPoint());
                if (row == -1) {
                    return;
                }
                updateIndexHint(row);
            }
        });

        myIndicesTable.setDefaultRenderer(Object.class, new MyCellRenderer());
        myIndicesTable.setDefaultRenderer(
            MavenIndicesManager.IndexUpdatingState.class,
            new MyIconCellRenderer()
        );

        myServiceList.getEmptyText().setText("No services");
        myIndicesTable.getEmptyText().setText("No remote repositories");

        updateButtonsState();
    }

    private void testServiceConnection(String url) {
        myTestButton.setEnabled(false);
        RepositoryAttachHandler.searchRepositories(
            myProject,
            Collections.singletonList(url),
            infos -> {
                myTestButton.setEnabled(true);
                if (infos.isEmpty()) {
                    Messages.showMessageDialog(
                        "No repositories found",
                        "Service Connection Failed",
                        UIUtil.getWarningIcon()
                    );
                }
                else {
                    final StringBuilder sb = new StringBuilder();
                    sb.append(infos.size()).append(infos.size() == 1 ? "repository" : " repositories").append(" found");
                    //for (MavenRepositoryInfo info : infos) {
                    //  sb.append("\n  ");
                    //  sb.append(info.getId()).append(" (").append(info.getName()).append(")").append(": ").append(info.getUrl());
                    //}
                    Messages.showMessageDialog(
                        sb.toString(),
                        "Service Connection Successful",
                        UIUtil.getInformationIcon()
                    );
                }
                return true;
            }
        );
    }

    private void updateButtonsState() {
        boolean hasSelection = !myIndicesTable.getSelectionModel().isSelectionEmpty();
        myUpdateButton.setEnabled(hasSelection);
    }

    public void updateIndexHint(int row) {
        MavenIndex index = getIndexAt(row);
        String message = index.getFailureMessage();
        if (message == null) {
            myIndicesTable.setToolTipText(null);
        }
        else {
            myIndicesTable.setToolTipText(message);
        }
    }

    private void doUpdateIndex() {
        myManager.scheduleUpdate(getSelectedIndices());
    }

    private List<MavenIndex> getSelectedIndices() {
        List<MavenIndex> result = new ArrayList<>();
        for (int i : myIndicesTable.getSelectedRows()) {
            result.add(getIndexAt(i));
        }
        return result;
    }

    private MavenIndex getIndexAt(int i) {
        MyTableModel model = (MyTableModel)myIndicesTable.getModel();
        return model.getIndex(i);
    }

    @Override
    public String getDisplayName() {
        return MavenIndicesLocalize.mavenRepositoriesTitle().get();
    }

    @Override
    public String getHelpTopic() {
        return "reference.settings.project.maven.repository.indices";
    }

    @Nonnull
    @Override
    public String getId() {
        return getHelpTopic();
    }

    @Override
    @RequiredUIAccess
    public JComponent createComponent(@Nonnull Disposable uiDisposable) {
        return myMainPanel;
    }

    @Override
    @RequiredUIAccess
    public void apply() throws ConfigurationException {
        MavenRepositoryServicesManager.getInstance().setUrls(myModel.getItems());
    }

    @Override
    @RequiredUIAccess
    public void reset() {
        myModel.removeAll();
        myModel.add(MavenRepositoryServicesManager.getInstance().getUrls());

        myIndicesTable.setModel(new MyTableModel(myManager.getIndices()));
        myIndicesTable.getColumnModel().getColumn(0).setPreferredWidth(400);
        myIndicesTable.getColumnModel().getColumn(1).setPreferredWidth(50);
        myIndicesTable.getColumnModel().getColumn(2).setPreferredWidth(50);
        myIndicesTable.getColumnModel().getColumn(3).setPreferredWidth(20);

        myUpdatingIcon = new AsyncProcessIcon(MavenIndicesLocalize.mavenIndicesUpdating().get());
        myUpdatingIcon.resume();

        myTimerListener = e -> myIndicesTable.repaint();
        myRepaintTimer = UIUtil.createNamedTimer("Maven repaint", AsyncProcessIcon.CYCLE_LENGTH / 20, myTimerListener);
        myRepaintTimer.start();
    }

    @Override
    @RequiredUIAccess
    public void disposeUIResources() {
        if (myRepaintTimer == null) {
            return; // has not yet been initialized and reset
        }

        myRepaintTimer.removeActionListener(myTimerListener);
        myRepaintTimer.stop();
        Disposer.dispose(myUpdatingIcon);
    }

    private class MyTableModel extends AbstractTableModel {
        private final String[] COLUMNS = new String[]{
            MavenIndicesLocalize.mavenIndexUrl().get(),
            MavenIndicesLocalize.mavenIndexType().get(),
            MavenIndicesLocalize.mavenIndexUpdated().get(),
            ""
        };

        private final List<MavenIndex> myIndices;

        public MyTableModel(List<MavenIndex> indices) {
            myIndices = indices;
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int index) {
            return COLUMNS[index];
        }

        @Override
        public int getRowCount() {
            return myIndices.size();
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 3) {
                return MavenIndicesManager.IndexUpdatingState.class;
            }
            return super.getColumnClass(columnIndex);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MavenIndex i = getIndex(rowIndex);
            switch (columnIndex) {
                case 0:
                    return i.getRepositoryPathOrUrl();
                case 1:
                    if (i.getKind() == MavenIndex.Kind.LOCAL) {
                        return "Local";
                    }
                    return "Remote";
                case 2:
                    if (i.getFailureMessage() != null) {
                        return MavenIndicesLocalize.mavenIndexUpdatedError().get();
                    }
                    long timestamp = i.getUpdateTimestamp();
                    if (timestamp == -1) {
                        return MavenIndicesLocalize.mavenIndexUpdatedNever().get();
                    }
                    return DateFormatUtil.formatDate(timestamp);
                case 3:
                    return myManager.getUpdatingState(i);
            }
            throw new RuntimeException();
        }

        public MavenIndex getIndex(int rowIndex) {
            return myIndices.get(rowIndex);
        }
    }

    private class MyCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column
        ) {
            // reset custom colors and let DefaultTableCellRenderer to set ones
            setForeground(null);
            setBackground(null);

            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            MavenIndex index = getIndexAt(row);
            if (index.getFailureMessage() != null) {
                if (isSelected) {
                    setForeground(JBColor.PINK);
                }
                else {
                    setBackground(JBColor.PINK);
                }
            }

            return c;
        }
    }

    private class MyIconCellRenderer extends MyCellRenderer {
        MavenIndicesManager.IndexUpdatingState myState;

        @Override
        public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column
        ) {
            myState = (MavenIndicesManager.IndexUpdatingState)value;
            return super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Dimension size = getSize();
            switch (myState) {
                case UPDATING:
                    myUpdatingIcon.setBackground(getBackground());
                    myUpdatingIcon.setSize(size.width, size.height);
                    myUpdatingIcon.paint(g);
                    break;
                case WAITING:
                    int x = (size.width - AllIcons.Process.Step_passive.getWidth()) / 2;
                    int y = (size.height - AllIcons.Process.Step_passive.getHeight()) / 2;
                    TargetAWT.to(AllIcons.Process.Step_passive).paintIcon(this, g, x, y);
                    break;
            }
        }
    }

    private static class URLInputVaslidator implements InputValidator {
        @Override
        @RequiredUIAccess
        public boolean checkInput(String inputString) {
            try {
                final URL url = new URL(inputString);
                return StringUtil.isNotEmpty(url.getHost());
            }
            catch (MalformedURLException e) {
                return false;
            }
        }

        @Override
        @RequiredUIAccess
        public boolean canClose(String inputString) {
            return checkInput(inputString);
        }
    }
}
