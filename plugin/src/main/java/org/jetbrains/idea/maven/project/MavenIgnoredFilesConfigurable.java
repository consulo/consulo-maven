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

import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ElementsChooser;
import consulo.util.io.FileUtil;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.utils.MavenUIUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.Strings;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.util.Collection;

public class MavenIgnoredFilesConfigurable implements SearchableConfigurable, Configurable.NoScroll {
    private static final char SEPARATOR = ',';

    private final MavenProjectsManager myManager;

    private Collection<String> myOriginallyIgnoredFilesPaths;
    private String myOriginallyIgnoredFilesPatterns;

    private JPanel myMainPanel;
    private ElementsChooser<String> myIgnoredFilesPathsChooser;
    private JTextArea myIgnoredFilesPattersEditor;

    public MavenIgnoredFilesConfigurable(Project project) {
        myManager = MavenProjectsManager.getInstance(project);
    }

    private void createUIComponents() {
        myIgnoredFilesPathsChooser = new ElementsChooser<>(true);
        myIgnoredFilesPathsChooser.getEmptyText().setText(MavenProjectLocalize.mavenIngoredNoFile().get());
    }

    @Override
    @RequiredUIAccess
    public JComponent createComponent(@Nonnull Disposable uiDisposable) {
        return myMainPanel;
    }

    @Override
    @RequiredUIAccess
    public void disposeUIResources() {
    }

    @Override
    @RequiredUIAccess
    public boolean isModified() {
        return !MavenUtil.equalAsSets(myOriginallyIgnoredFilesPaths, myIgnoredFilesPathsChooser.getMarkedElements()) ||
            !myOriginallyIgnoredFilesPatterns.equals(myIgnoredFilesPattersEditor.getText());
    }

    @Override
    @RequiredUIAccess
    public void apply() throws ConfigurationException {
        myManager.setIgnoredFilesPaths(myIgnoredFilesPathsChooser.getMarkedElements());
        myManager.setIgnoredFilesPatterns(Strings.tokenize(myIgnoredFilesPattersEditor.getText(), Strings.WHITESPACE + SEPARATOR));
    }

    @Override
    @RequiredUIAccess
    public void reset() {
        myOriginallyIgnoredFilesPaths = myManager.getIgnoredFilesPaths();
        myOriginallyIgnoredFilesPatterns = Strings.detokenize(myManager.getIgnoredFilesPatterns(), SEPARATOR);

        MavenUIUtil.setElements(
            myIgnoredFilesPathsChooser,
            MavenUtil.collectPaths(myManager.getProjectsFiles()),
            myOriginallyIgnoredFilesPaths,
            FileUtil::comparePaths
        );
        myIgnoredFilesPattersEditor.setText(myOriginallyIgnoredFilesPatterns);
    }

    @Override
    public String getDisplayName() {
        return MavenProjectLocalize.mavenTabIgnoredFiles().get();
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return "reference.settings.project.maven.ignored.files";
    }

    @Nonnull
    @Override
    public String getId() {
        return getHelpTopic();
    }

    @Override
    public Runnable enableSearch(String option) {
        return null;
    }
}
