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
package org.jetbrains.idea.maven.dom.refactorings.extract;

import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Set;
import java.util.function.Function;

public class SelectMavenProjectDialog extends DialogWrapper {
    private final Set<MavenDomProjectModel> myMavenDomProjectModels;
    private final boolean myHasExclusions;

    private JComboBox myMavenProjectsComboBox;
    private JPanel myMainPanel;
    private JCheckBox myReplaceAllCheckBox;
    private JCheckBox myExtractExclusions;
    private boolean myHasUsagesInProjects = false;

    private ItemListener myReplaceAllListener;
    private final Function<MavenDomProjectModel, Set<MavenDomDependency>> myOccurrencesCountFunction;

    public SelectMavenProjectDialog(
        @Nonnull Project project,
        @Nonnull Set<MavenDomProjectModel> mavenDomProjectModels,
        @Nonnull Function<MavenDomProjectModel, Set<MavenDomDependency>> funOccurrences,
        boolean hasExclusions
    ) {
        super(project, true);
        myMavenDomProjectModels = mavenDomProjectModels;
        myHasExclusions = hasExclusions;

        setTitle(MavenDomBundle.message("choose.project"));

        myOccurrencesCountFunction = funOccurrences;
        for (MavenDomProjectModel model : myMavenDomProjectModels) {
            if (myOccurrencesCountFunction.apply(model).size() > 0) {
                myHasUsagesInProjects = true;
                break;
            }
        }

        init();
    }

    @Nonnull
    protected Action[] createActions() {
        return new Action[]{
            getOKAction(),
            getCancelAction()
        };
    }

    protected void init() {
        super.init();

        updateOkStatus();
    }

    @Override
    protected void dispose() {
        super.dispose();
        if (myReplaceAllCheckBox != null) {
            myReplaceAllCheckBox.removeItemListener(myReplaceAllListener);
        }
    }

    @Nullable
    public MavenDomProjectModel getSelectedProject() {
        return (MavenDomProjectModel)ComboBoxUtil.getSelectedValue((DefaultComboBoxModel)myMavenProjectsComboBox.getModel());
    }

    public boolean isReplaceAllOccurrences() {
        return myReplaceAllCheckBox.isSelected();
    }

    public boolean isExtractExclusions() {
        return myExtractExclusions.isSelected();
    }

    protected JComponent createCenterPanel() {
        ComboBoxUtil.setModel(myMavenProjectsComboBox, new DefaultComboBoxModel(), myMavenDomProjectModels,
            model -> {
                String projectName = model.getName().getStringValue();
                MavenProject mavenProject = MavenDomUtil.findProject(model);
                if (mavenProject != null) {
                    projectName = mavenProject.getDisplayName();
                }
                if (StringUtil.isEmptyOrSpaces(projectName)) {
                    projectName = "pom.xml";
                }
                return Pair.create(projectName, model);
            }
        );

        myReplaceAllListener = new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                updateControls();
            }
        };

        myMavenProjectsComboBox.addItemListener(myReplaceAllListener);
        myMavenProjectsComboBox.setSelectedItem(0);
        myReplaceAllCheckBox.setVisible(myHasUsagesInProjects);
        myExtractExclusions.setVisible(myHasExclusions);

        updateControls();

        return myMainPanel;
    }

    private void updateControls() {
        MavenDomProjectModel project = getSelectedProject();
        Integer count = myOccurrencesCountFunction.apply(project).size();
        myReplaceAllCheckBox.setText(RefactoringBundle.message("replace.all.occurences", count));

        myReplaceAllCheckBox.setEnabled(count != 0);
    }

    private void updateOkStatus() {
        setOKActionEnabled(getSelectedProject() != null);
    }

    public JComponent getPreferredFocusedComponent() {
        return myMavenProjectsComboBox;
    }
}