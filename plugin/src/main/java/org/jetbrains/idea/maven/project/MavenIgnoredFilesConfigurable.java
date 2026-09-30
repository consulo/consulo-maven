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
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.ComponentItemRender;
import consulo.ui.Table;
import consulo.ui.TableItemEditor;
import consulo.ui.TextArea;
import consulo.ui.ValueComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.util.io.FileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.Strings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MavenIgnoredFilesConfigurable implements SearchableConfigurable, Configurable.NoScroll {
    private static final char SEPARATOR = ',';

    private final MavenProjectsManager myManager;

    private final MutableFlatDataModel<String> myFiles = FlatDataModel.of(List.of());
    private final Set<String> myIgnoredFiles = new HashSet<>();

    private Collection<String> myOriginallyIgnoredFilesPaths = List.of();
    private String myOriginallyIgnoredFilesPatterns = "";

    @Nullable
    private TextArea myIgnoredFilesPatternsEditor;

    public MavenIgnoredFilesConfigurable(Project project) {
        myManager = MavenProjectsManager.getInstance(project);
    }

    @Override
    @RequiredUIAccess
    public Component createUIComponent(@Nonnull Disposable uiDisposable) {
        TextArea patternsEditor = TextArea.create();
        myIgnoredFilesPatternsEditor = patternsEditor;

        Table<String> filesTable = Table.create(myFiles);
        filesTable.setShowHeader(false);
        filesTable.addColumn(LocalizeValue.empty(), myIgnoredFiles::contains)
            .setWidth(40)
            .setRender(ComponentItemRender.reusable(
                () -> CheckBox.create(LocalizeValue.empty()),
                (checkBox, item) -> checkBox.setValue(Boolean.TRUE.equals(item.getValue()))
            ))
            .setEditor(new TableItemEditor<>() {
                @Override
                public ValueComponent<Boolean> createComponent(String path) {
                    return CheckBox.create(LocalizeValue.empty(), myIgnoredFiles.contains(path));
                }

                @Override
                public void commit(String path, @Nullable Boolean value) {
                    if (Boolean.TRUE.equals(value)) {
                        myIgnoredFiles.add(path);
                    }
                    else {
                        myIgnoredFiles.remove(path);
                    }
                }
            });
        filesTable.addColumn(LocalizeValue.empty(), path -> path);

        return DockLayout.create()
            .top(LabeledLayout.create(MavenProjectLocalize.mavenIgnoredFilesPatterns(), DockLayout.create().center(patternsEditor)))
            .center(LabeledLayout.create(
                MavenProjectLocalize.mavenIgnoredFilesList(),
                DockLayout.create().center(ScrollableLayout.create(filesTable))
            ));
    }

    @Override
    @RequiredUIAccess
    public void disposeUIResources() {
        myIgnoredFilesPatternsEditor = null;
    }

    @Override
    @RequiredUIAccess
    public boolean isModified() {
        TextArea patternsEditor = myIgnoredFilesPatternsEditor;
        if (patternsEditor == null) {
            return false;
        }

        return !MavenUtil.equalAsSets(myOriginallyIgnoredFilesPaths, getIgnoredFiles())
            || !myOriginallyIgnoredFilesPatterns.equals(patternsEditor.getValue());
    }

    @Override
    @RequiredUIAccess
    public void apply() throws ConfigurationException {
        TextArea patternsEditor = myIgnoredFilesPatternsEditor;
        if (patternsEditor == null) {
            return;
        }

        myManager.setIgnoredFilesPaths(getIgnoredFiles());
        myManager.setIgnoredFilesPatterns(Strings.tokenize(patternsEditor.getValue(), Strings.WHITESPACE + SEPARATOR));
    }

    @Override
    @RequiredUIAccess
    public void reset() {
        myOriginallyIgnoredFilesPaths = myManager.getIgnoredFilesPaths();
        myOriginallyIgnoredFilesPatterns = Strings.detokenize(myManager.getIgnoredFilesPatterns(), SEPARATOR);

        List<String> files = new ArrayList<>(MavenUtil.collectPaths(myManager.getProjectsFiles()));
        files.sort(FileUtil::comparePaths);

        myIgnoredFiles.clear();
        for (String file : files) {
            if (myOriginallyIgnoredFilesPaths.contains(file)) {
                myIgnoredFiles.add(file);
            }
        }
        myFiles.replaceAll(files);

        TextArea patternsEditor = myIgnoredFilesPatternsEditor;
        if (patternsEditor != null) {
            patternsEditor.setValue(myOriginallyIgnoredFilesPatterns);
        }
    }

    private List<String> getIgnoredFiles() {
        List<String> ignoredFiles = new ArrayList<>();
        for (String file : myFiles) {
            if (myIgnoredFiles.contains(file)) {
                ignoredFiles.add(file);
            }
        }
        return ignoredFiles;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return MavenProjectLocalize.mavenTabIgnoredFiles();
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
}
